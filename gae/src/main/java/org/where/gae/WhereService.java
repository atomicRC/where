package org.example.where.gae;

import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;
import com.google.api.server.spi.ServiceException;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.appengine.api.users.User;
import com.googlecode.objectify.ObjectifyService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Named;

import static com.googlecode.objectify.ObjectifyService.ofy;

/**
 * Defines v1 of a 'where' API, which provides simple "whereIs" method.
 */
@Api(
        name = "where",
        version = "v1",
        scopes = {Constants.EMAIL_SCOPE},
        clientIds = {Constants.WEB_LOCALHOST_CLIENT_ID,  // localhost test
                Constants.WEB_APPSPOT_CLIENT_ID,         // https://unique-voyage-610.appspot.com/
                Constants.ANDROID_CLIENT_ID,             // Florian's debug key
                Constants.ANDROID_MATTHIAS,              // Matthias's debug key
                Constants.ANDROID_WHERE,                 // where-certificate debug
                Constants.IOS_CLIENT_ID,                 // not set
                Constants.API_EXPLORER_CLIENT_ID},       // Google Explorer API
        audiences = {Constants.ANDROID_AUDIENCE}
)
public class WhereService {
    /* initialize Objectify */
    static {
        ObjectifyService.register(GcmReqistration.class);
    }

    private static final Logger logger = Logger.getLogger(WhereService.class.getName());

    public static final int UNKNOWN_USER = 1;
    public static final int INACTIVE_USER = 2;
    public static final int CANNOT_SEND_GCM_MESSAGE = 3;

    private static WhereService instance;

    //private ConcurrentMap<String, String> gcmRegistrations = new ConcurrentHashMap<>();
    private ConcurrentMap<String, Set<String>> locationRequestsMap = new ConcurrentHashMap<>();
    private Set<String> activeUsers = new ConcurrentSkipListSet<>();

    public WhereService() {
        instance = this;
    }

    @ApiMethod(name = "register", httpMethod = "post")
    public WhereStatus registerDevice(User user, @Named("gcm_registration_id") String gcmRegistrationId) throws ServiceException {
        logger.info("GCM registration of user: " + user);

        if (user == null) {
            throw new ServiceException(401, new NullPointerException("user"));
        }

        if (gcmRegistrationId == null) {
            throw new ServiceException(400, new NullPointerException("gcm_registration_id"));
        }

        String email = formatEmail(user.getEmail());

        logger.info("user " + email + " has registered.");

//        gcmRegistrations.put(user.getEmail(), gcmRegistrationId);
        ofy().save().entity(new GcmReqistration(email, gcmRegistrationId)).now();

        return new WhereStatus("registration is ok");
    }

    @ApiMethod(name = "locate", httpMethod = "post")
    public WhereStatus requestLocation(User user, @Named("email") String email) throws ServiceException {
        logger.info("user: " + user + " email: " + email);

        if (user == null) {
            throw new ServiceException(401, new NullPointerException("user"));
        }

        if (email == null) {
            throw new ServiceException(400, new NullPointerException("email"));
        }

        String requesterEmail = formatEmail(user.getEmail());
        email = formatEmail(email);

//        String gcmRegiId = gcmRegistrations.get(email);
        GcmReqistration gcmReqistration = ofy().load().type(GcmReqistration.class).id(email).now();

        if (gcmReqistration == null) {
            // user is not known
            return new WhereStatus(UNKNOWN_USER, email + " is unknown");
        } else {
            if (!activeUsers.contains(email)) {
                logger.info(email + " is NOT active");
                return new WhereStatus(INACTIVE_USER, email + " is NOT active right now");
            }

            String gcmRegiId = gcmReqistration.gcmId;

            try {
                logger.info("Sending GCM message to " + email + " from " + requesterEmail);
                Map<String, String> message = new HashMap<>();
                message.put("requester", requesterEmail);
                message.put("name", user.getNickname());
                message.put("action", "locate");
                sendMessageToDevice(gcmRegiId, message);

                /* enqueue current user's request */
                locationRequestsMap.putIfAbsent(email, new ConcurrentSkipListSet<String>());
                locationRequestsMap.get(email).add(requesterEmail);
            } catch (IOException e) {
                logger.log(Level.WARNING, "cannot send GCM message", e);
                return new WhereStatus(CANNOT_SEND_GCM_MESSAGE, "cannot send message to " + email);
            }


            return new WhereStatus("accepted");
        }
    }

    @ApiMethod(name = "isRegistered", httpMethod = "post")
    public WhereStatus isRegistered(User user, @Named("email") String email) throws ServiceException {
        logger.info("user: " + user + " email: " + email);

        if (user == null) {
            throw new ServiceException(401, new NullPointerException("user"));
        }

        if (email == null) {
            throw new ServiceException(400, new NullPointerException("email"));
        }

        String requesterEmail = formatEmail(user.getEmail());
        email = formatEmail(email);

        GcmReqistration gcmReqistration = ofy().load().type(GcmReqistration.class).id(email).now();

        if (gcmReqistration == null) {
            logger.info("user is NOT registered.");
            return new WhereStatus(UNKNOWN_USER, "unknown user: " + email);
        } else {
            boolean isActive = activeUsers.contains(email);

            String status = "'" + email + "' is registered and " + (isActive ? "" : "in") + "active";
            logger.info(status);
            return new WhereStatus(status);
        }
    }

    @ApiMethod(name = "setActiveFlag", httpMethod = "post")
    public WhereStatus setActiveFlag(User user, @Named("active") boolean active) throws ServiceException {
        logger.info("user: " + user + " active: " + active);

        if (user == null) {
            throw new ServiceException(401, new NullPointerException("user"));
        }

        String requesterEmail = formatEmail(user.getEmail());

        GcmReqistration gcmReqistration = ofy().load().type(GcmReqistration.class).id(requesterEmail).now();

        if (gcmReqistration == null) {
            logger.info("user is NOT registered.");
            return new WhereStatus(UNKNOWN_USER, "unknown user: " + requesterEmail);
        } else {
            logger.info("user is registered, will set his/her status to " + (active?"active":"inactive"));
            if (active) {
                activeUsers.add(requesterEmail);
            } else {
                activeUsers.remove(requesterEmail);
            }
            return new WhereStatus("ok. status set to " + (active?"active":"inactive"));
        }
    }

    @ApiMethod(name = "publish", httpMethod = "post")
    public WhereStatus publishLocation(User user, WhereLocation location) throws ServiceException {
        logger.info("user: " + user + " has location: " + location);

        if (user == null) {
            throw new ServiceException(401, new NullPointerException("user"));
        }

        if (location == null) {
            throw new ServiceException(400, new NullPointerException("location"));
        }

        int locationPushCount = 0;

        String requesterEmail = formatEmail(user.getEmail());

        Set<String> requesters = locationRequestsMap.remove(requesterEmail);
        if (requesters == null) {
            logger.info("Received unsolicited location update from " + requesterEmail);
        } else {
            Map<String, String> messageMap = new HashMap<>();
            messageMap.put("action", "disclose");
            messageMap.put("user", requesterEmail);
            messageMap.put("location", location.getLocation());
            messageMap.put("activity", location.getActivity());

            for (String requester : requesters) {
                if (!activeUsers.contains(requester)) {
                    logger.info("Unable to deliver location update to " + requester + ": user is NOT active");
                    continue;
                }

                GcmReqistration gcmReqistration = ofy().load().type(GcmReqistration.class).id(requester).now();
//                String gcmId = gcmRegistrations.get(requester);
//                if (gcmId == null) {
                if (gcmReqistration == null) {
                    logger.info("Unable to deliver location update to " + requester + ": GCM ID is unknown");
                } else {
                    try {
                        String gcmId = gcmReqistration.gcmId;
                        sendMessageToDevice(gcmId, messageMap);

                        locationPushCount++;
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Cannot send GCM location-message to " + requester, e);
                    }
                }
            }
        }

        return new WhereStatus("location received and successfully sent to " + locationPushCount +
                " user" + (locationPushCount==1?"":"s"));
    }

    /* service status methods. */
    public static int getLocationRequestsCount() {
        int count = 0;

        if (instance != null) {
            count = instance.locationRequestsMap.size();
        }

        return count;
    }

    private Result sendMessageToDevice(String gcmTargetId, Map<String, String> messageMap) throws IOException {
        Sender sender = new Sender(Constants.API_KEY);

        Message.Builder message = new Message.Builder();
        for (String key : messageMap.keySet()) {
            String value = messageMap.get(key);
            message.addData(key, value);
        }

        return sender.send(message.build(), gcmTargetId, 5);
    }

    /* remove dot from username and replace googlemail with gmail */
    private String formatEmail(String email) {
        int atPos = email.indexOf('@');
        int dotPos = email.indexOf(".");
        while (atPos != -1 && dotPos != -1 && dotPos < atPos) {
            email = email.replaceFirst("\\.","");
            dotPos = email.indexOf(".");
        }
        email = email.replaceAll("@googlemail.com", "@gmail.com");

        return email.toLowerCase();
    }
}
