/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import java.util.List;

import org.dspace.content.WorkspaceItem;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;

/**
 * A command-line tool for re
 * relationships. Takes community DB Id or handle arguments as inputs.
 *
 * @author rrodgers
 * @version $Revision$
 */

public class RemoveOrphanWorkspaceItem {

    protected WorkspaceItemService workspaceItemService;

    public RemoveOrphanWorkspaceItem() {
        workspaceItemService = ContentServiceFactory.getInstance().getWorkspaceItemService();
    }

    /**
     * @param argv the command line arguments given
     * @throws Exception if error
     */
    public static void main(String[] argv) throws Exception {
        RemoveOrphanWorkspaceItem remover = new RemoveOrphanWorkspaceItem();
        try (Context c = new Context();) {
            c.turnOffAuthorisationSystem();
            remover.deleteOrphan(c);
            c.restoreAuthSystemState();;
            c.complete();
        }
    }

    private void deleteOrphan(Context context) throws Exception {
        int orphanItems = workspaceItemService.countByEPerson(context, null);
        System.out.println("Found " + orphanItems + " items");
        int pages = orphanItems / 20 + 1;
        int count = 0;
        for (int idx = 0; idx < pages; idx++) {
            System.out.println("processing page " + idx);
            // don't use the offset as we are deleting all the item page by page
            List<WorkspaceItem> wsitems = workspaceItemService.findByEPerson(context, null, 20, 0);
            for (WorkspaceItem wsi : wsitems) {
                System.out.println("Deleting workspace item #" + (count++) + " id " + wsi.getID());
                workspaceItemService.deleteAll(context, wsi);
            }
            context.commit();
            context.clear();
        }
    }

}
