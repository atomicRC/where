package org.example.where.service;

import android.os.Binder;

/**
 * Created by Florian Antonescu.
 */
public class WhereBinder extends Binder {
    private WhereService service;

    public WhereBinder(WhereService service) {
        this.service = service;
    }

    public WhereService getService() {
        return service;
    }
}
