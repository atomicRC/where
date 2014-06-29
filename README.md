where
=====

Where app for Android Wear using Google App Engine. 

Notice:
This app was written for the Google Android Wear Preview SDK. It is currently not compatible to the latest
Android Wear SDK.

This application uses Google App Engine for receiving queries from the Where Android app. The GAE server application 
uses (1) Google Cloud Endpoints for exposing services to the Android app and (2) Google Cloud Messaging for pushing
messages to the Android phones. This repository contains the code for both projects.

In order to prepare the application the following steps needs to be performed:

1. Create a Google App Engine project
2. Enable the followign APIs
3. - Google Plus API
4. - Google Cloud Messaging for Android
5. Create Credentials for
6. - Android
7. - Web access
8. Configure the credentials in the configuration.properties file in the android folder
9. Deploy the GAE application
10. Install the Wear app
