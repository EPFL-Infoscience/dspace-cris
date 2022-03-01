/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *  http://www.dspace.org/license/
 */
package org.dspace.builder;

import java.sql.SQLException;
import java.util.UUID;

import org.dspace.core.Context;
import org.dspace.deduplication.Deduplication;
import org.dspace.deduplication.service.DeduplicationService;

/**
 * Builder to construct deduplication decisions in test cases
 * 
 * @author Francesco Pio Scognamiglio (francescopio.scognamiglio at 4science.it)
 */
public class DeduplicationSignatureBuilder extends AbstractBuilder<Deduplication, DeduplicationService> {

    private Deduplication deduplication;

    private String action;
    private boolean check;

    protected DeduplicationSignatureBuilder(Context context) {
        super(context);
    }

    public static DeduplicationSignatureBuilder createDeduplication(final Context context) throws SQLException {
        DeduplicationSignatureBuilder builder = new DeduplicationSignatureBuilder(context);
        return builder.create();
    }

    private DeduplicationSignatureBuilder create() throws SQLException {
        deduplication = deduplicationService.create(context, new Deduplication());
        return this;
    }

    public DeduplicationSignatureBuilder withDeduplicationId(final Integer deduplicationId) {
        deduplication.setDeduplicationId(deduplicationId);
        return this;
    }

    public DeduplicationSignatureBuilder withFake(final boolean fake) {
        deduplication.setFake(fake);
        return this;
    }

    public DeduplicationSignatureBuilder withToFix(final boolean toFix) {
        deduplication.setTofix(toFix);
        return this;
    }

    public DeduplicationSignatureBuilder withNote(final String note) {
        deduplication.setNote(note);
        return this;
    }

    public DeduplicationSignatureBuilder withReaderNote(final String readerNote) {
        deduplication.setReaderNote(readerNote);
        return this;
    }

    public DeduplicationSignatureBuilder withSubmitterDecision() {
        check = false;
        return this;
    }

    public DeduplicationSignatureBuilder withWorkflowDecision() {
        check = true;
        return this;
    }

    public DeduplicationSignatureBuilder withFirstItemId(final UUID firstItemId) {
        deduplication.setFirstItemId(firstItemId);
        return this;
    }

    public DeduplicationSignatureBuilder withSecondItemId(final UUID secondItemId) {
        deduplication.setSecondItemId(secondItemId);
        return this;
    }

    public DeduplicationSignatureBuilder withVerifyAction() {
        action = "verify";
        return this;
    }

    public DeduplicationSignatureBuilder withRejectAction() {
        action = "reject";
        return this;
    }

    public DeduplicationSignatureBuilder withAdminRejectAction() {
        action = "adminreject";
        return this;
    }

    @Override
    public Deduplication build() {
        try {
            deduplicationService.update(context, deduplication);

            dedupUtils.verifyOrRejectDups(context, deduplication, action, check);

            dedupUtils.getDedupService().commit();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return deduplication;
    }

    @Override
    public void cleanup() throws Exception {
        if (deduplication != null) {
            try (Context c = new Context()) {
                c.turnOffAuthorisationSystem();
                Deduplication attachedDso = c.reloadEntity(deduplication);
                if (attachedDso != null) {
                    delete(c, attachedDso);
                }
            }
        }
    }

    @Override
    public void delete(Context c, Deduplication deduplication) throws Exception {
        if (deduplication != null) {
            getService().delete(c, deduplication);
        }
        c.complete();
        dedupUtils.getDedupService().commit();
    }

    @Override
    protected DeduplicationService getService() {
        return deduplicationService;
    }
}
