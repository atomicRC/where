package org.example.where.gae;

import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

/**
 * User: Florian Antonescu
 * Email: alexandru-florian.antonescu@sap.com
 * Date: 17/06/14
 * Time: 00:22
 */
@Entity
@Cache
public class GcmReqistration {
    @Id
    String email;
    String gcmId;

    public GcmReqistration() {
    }

    public GcmReqistration(String email, String gcmId) {
        this.email = email;
        this.gcmId = gcmId;
    }
}
