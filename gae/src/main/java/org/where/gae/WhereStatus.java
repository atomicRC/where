package org.example.where.gae;

/**
 * User: Florian Antonescu
 * Email: alexandru-florian.antonescu@sap.com
 * Date: 15/06/14
 * Time: 13:51
 */
public class WhereStatus {
    private int code;
    private String message;

    public WhereStatus(String message) {
        this.message = message;
    }

    public WhereStatus(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
