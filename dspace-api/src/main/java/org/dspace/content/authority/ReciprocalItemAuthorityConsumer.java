/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.authority;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.dspace.services.ConfigurationService;
import org.dspace.utils.DSpace;

/**
 * Sample consumer to link a dspace item with another (i.e a publication with
 * the corresponding dataset or viceversa)
 *
 * @author Andrea Bollini
 * @version $Revision $
 */
public class ReciprocalItemAuthorityConsumer implements Consumer
{
    private static final Logger log = Logger.getLogger(ReciprocalItemAuthorityConsumer.class);
    
    private Map<String, String> reciprocalMetadata = new ConcurrentHashMap<String, String>();
    
    private transient Set<UUID> processedHandles = new HashSet<UUID>();
    
    private ItemService itemService;
    
	public ReciprocalItemAuthorityConsumer() {
		ConfigurationService confService = new DSpace().getConfigurationService();
		itemService = ContentServiceFactory.getInstance().getItemService();
		for (String conf : confService.getPropertyKeys("ItemAuthority.reciprocalMetadata")) {
			reciprocalMetadata.put(conf.substring("ItemAuthority.reciprocalMetadata.".length()),
					confService.getProperty(conf));
			reciprocalMetadata.put(confService.getProperty(conf),
					conf.substring("ItemAuthority.reciprocalMetadata.".length()));
	}
	}
    
    public void initialize()
        throws Exception
    {
       
    }

    public void consume(Context ctx, Event event)
        throws Exception
    {
    	try
    	{
	    	ctx.turnOffAuthorisationSystem();
	    	Item item = (Item) event.getSubject(ctx);
	    	if (item == null || !item.isArchived()) return;
	    	if (processedHandles.contains(item.getID())) {
	    		return;
	    	}
	    	else {
	    		processedHandles.add(item.getID());
	    	}
	        if (reciprocalMetadata != null) {
	        	for (String k : reciprocalMetadata.keySet()) {
	        		String entityType = k.split("\\.", 2)[0];
	        		String metadata = k.split("\\.", 2)[1];
	        		checkItemRefs(ctx, item, entityType, metadata, reciprocalMetadata.get(k));
	        	}
	        }
    	}
    	finally {
    		ctx.restoreAuthSystemState();
    	}
    }

	private void checkItemRefs(Context ctx, Item item, String entityType, String metadata, String reciprocalMetadata)
			throws SQLException {
		
		// only process the reciprocal metadata for the appropriate entity type
		if (!StringUtils.equalsIgnoreCase(itemService.getEntityType(item), entityType)) {
			return;
		}
		List<MetadataValue> meta = itemService.getMetadataByMetadataString(item, metadata);
		if (meta != null) {
			for (MetadataValue md : meta) {
				if (md.getAuthority() != null && md.getConfidence() == Choices.CF_ACCEPTED) {
					try {
						UUID id = UUID.fromString(md.getAuthority());
						Item target = itemService.find(ctx, id);
			    		if (target != null) {
			    			assureReciprocalLink(ctx, target, reciprocalMetadata, item.getName(), item.getID().toString());
			    		}
					} catch (IllegalArgumentException e) {
						// if the authority is not an uuid nothing is needed
					}
				}
			}
		}
	}

	private void assureReciprocalLink(Context ctx, Item target, String mdString, String name, String sourceUuid)
			throws SQLException {
		List<MetadataValue> meta = target.getItemService().getMetadataByMetadataString(target, mdString);
		String[] mdSplit = mdString.split("\\.");
		if (meta != null) {
			for (MetadataValue md : meta) {
				if (StringUtils.equals(md.getAuthority(), sourceUuid)) {
					return;
				}
			}
		}
		itemService.addMetadata(ctx, target, mdSplit[0], mdSplit[1], mdSplit.length > 2 ? mdSplit[2] : null, null, name,
				sourceUuid, Choices.CF_ACCEPTED);
	}

	public void end(Context ctx)
        throws Exception
    {
    	// nothing
		processedHandles.clear();
    }
    
    public void finish(Context ctx) 
    {
    	// nothing
    }

}