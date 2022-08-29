/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authenticate.service;

public class EpflClientException extends RuntimeException {

    private static final long serialVersionUID = -7618061110212398216L;

    private int status = 0;

    public EpflClientException(int status, String content) {
        super(content);
        this.status = status;
    }

    public EpflClientException(Throwable cause) {
        super(cause);
    }

    public int getStatus() {
        return this.status;
    }

}

