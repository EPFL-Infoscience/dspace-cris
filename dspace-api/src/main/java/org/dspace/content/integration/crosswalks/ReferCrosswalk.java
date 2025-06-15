/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.crosswalk.CrosswalkMode;
import org.dspace.content.crosswalk.CrosswalkObjectNotSupported;
import org.dspace.content.integration.crosswalks.evaluators.ConditionEvaluator;
import org.dspace.content.integration.crosswalks.evaluators.ConditionEvaluatorMapper;
import org.dspace.content.integration.crosswalks.model.TemplateLine;
import org.dspace.content.integration.crosswalks.virtualfields.VirtualField;
import org.dspace.content.integration.crosswalks.virtualfields.VirtualFieldMapper;
import org.dspace.content.security.service.MetadataSecurityService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.discovery.configuration.DiscoveryConfigurationUtilsService;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.service.GroupService;
import org.dspace.services.ConfigurationService;
import org.dspace.util.UUIDUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;

/**
 * Implementation of {@link ItemExportCrosswalk} to produce an output from an
 * Item starting from a template.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public class ReferCrosswalk implements ItemExportCrosswalk {

    private static final String COUNTER_FIELD = "#items.counter#";
    private static final String TOTAL_FIELD = "#items.total#";
    private static final String OFFSET_FIELD = "#items.offset#";

    private static Logger log = LogManager.getLogger(ReferCrosswalk.class);

    private static final Pattern FIELD_PATTERN = Pattern.compile("@(.*)@");

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private DiscoveryConfigurationUtilsService searchConfigurationUtilsService;

    @Autowired
    private VirtualFieldMapper virtualFieldMapper;

    @Autowired
    private ConditionEvaluatorMapper conditionEvaluatorMapper;

    @Autowired
    private MetadataSecurityService metadataSecurityService;

    @Autowired
    private GroupService groupService;

    private Converter<String, String> converter;

    /**
     * Post processor applied to the generated output for an individual item.
     * When multiple items are exported via {@link #disseminate(Context, Iterator, OutputStream)}
     * no post processing is invoked on the template lines not related to the single item.
     * This allow to give flexibility in the generation of the template without preventing us to
     * output the results progressively as soon as an individual item has been worked on
     */
    private Consumer<List<String>> linesPostProcessor;

    private String multipleItemsTemplateFileName;

    private String templateFileName;

    private String mimeType;

    private String fileName;

    private String entityType;

    private boolean publiclyReadable = false;

    private List<TemplateLine> templateLines;

    private List<TemplateLine> multipleItemsTemplateLines;

    private CrosswalkMode crosswalkMode;

    private List<String> allowedGroups;

    private boolean findRelatedItems = false;

    private boolean fastMetadataSecurity = false;

    @PostConstruct
    private void postConstruct() throws IOException {
        String parent = configurationService.getProperty("dspace.dir") + File.separator + "config" + File.separator;
        File templateFile = new File(parent, templateFileName);
        this.templateLines = readTemplateLines(templateFile);

        if (StringUtils.isNotBlank(multipleItemsTemplateFileName)) {
            File multipleItemsTemplateFile = new File(parent, multipleItemsTemplateFileName);
            this.multipleItemsTemplateLines = readTemplateLines(multipleItemsTemplateFile);
        }
    }

    @Override
    public boolean isAuthorized(Context context) {
        if (CollectionUtils.isEmpty(allowedGroups)) {
            return true;
        }

        EPerson ePerson = context.getCurrentUser();
        if (ePerson == null) {
            return allowedGroups.contains(Group.ANONYMOUS);
        }

        return allowedGroups.stream()
            .anyMatch(groupName -> isMemberOfGroupNamed(context, ePerson, groupName));
    }

    @Override
    public void disseminate(Context context, DSpaceObject dso, OutputStream out)
        throws CrosswalkException, IOException, SQLException, AuthorizeException {

        if (!canDisseminate(context, dso)) {
            throw new CrosswalkObjectNotSupported("Can only crosswalk an Item with the configured type: " + entityType);
        }

        if (!isAuthorized(context)) {
            throw new AuthorizeException("The current user is not allowed to perform a zip item export");
        }
        try (OutputStreamWriter osw = new OutputStreamWriter(out, UTF_8);
                BufferedWriter writer = new BufferedWriter(osw)) {
            List<String> lines = getItemLines(context, dso, true);
            writeLines(writer, lines);
            writer.newLine();
            writer.flush();
        }
    }

    @Override
    public void disseminate(Context context, Iterator<? extends DSpaceObject> dsoIterator, Integer total,
            Integer offset, Integer size, OutputStream out)
        throws CrosswalkException, IOException, SQLException, AuthorizeException {

        if (CollectionUtils.isEmpty(multipleItemsTemplateLines)) {
            throw new UnsupportedOperationException("No template defined for multiple items");
        }

        if (!isAuthorized(context)) {
            throw new AuthorizeException("The current user is not allowed to perform a zip item export");
        }

        try (OutputStreamWriter osw = new OutputStreamWriter(out, UTF_8);
                BufferedWriter writer = new BufferedWriter(osw)) {
            List<String> multiLines = new ArrayList<String>();
            boolean itemProcessed = false;
            for (TemplateLine line : multipleItemsTemplateLines) {
                if (line.isTemplateField()) {
                    if (multiLines.size() > 0) {
                        if (itemProcessed) {
                            writer.newLine();
                            itemProcessed = false;
                        }
                        writeLines(writer, multiLines);
                        writer.newLine();
                    }
                    multiLines.clear();
                    boolean first = true;
                    boolean afterPreviousItem = false;
                    while (dsoIterator.hasNext()) {
                        DSpaceObject dso = dsoIterator.next();
                        if (!canDisseminate(context, dso)) {
                            throw new CrosswalkObjectNotSupported(
                                "Can only crosswalk items with the configured type: " + entityType);
                        }
                        List<String> lines = getSingleItemLines(context, dso, line);
                        if (lines.size() > 0) {
                            if (afterPreviousItem && StringUtils.isNotBlank(line.getAfterField())) {
                                lines.add(0, line.getAfterField());
                            } else if (itemProcessed) {
                                writer.newLine();
                            }
                            if (first) {
                                first = false;
                            } else if (StringUtils.isNotBlank(line.getBeforeField())) {
                                lines.add(line.getBeforeField());
                            }
                            itemProcessed = true;
                        }
                        if (dsoIterator.hasNext()) {
                            afterPreviousItem = true;
                        }
                        writeLines(writer, lines);
                    }
                } else {
                    multiLines.add(transformMultiTemplateLine(dsoIterator, total, offset, size, line.getBeforeField()));
                }
            }
            if (itemProcessed) {
                writer.newLine();
            }
            if (multiLines.size() > 0) {
                writeLines(writer, multiLines);
                writer.newLine();
            }
            writer.flush();
        }
    }

    private String transformMultiTemplateLine(Iterator<? extends DSpaceObject> dsoIterator, Integer total,
            Integer offset, Integer size, String beforeField) {
        String output = beforeField;
        if (total != null) {
            output = output.replace(TOTAL_FIELD, String.valueOf(total));
        }
        if (offset != null) {
            output = output.replace(OFFSET_FIELD, String.valueOf(offset));
        }
        if (size != null) {
            output = output.replace(COUNTER_FIELD, String.valueOf(size));
        }
        return output;
    }

    @Override
    public boolean canDisseminate(Context context, DSpaceObject dso) {
        return dso.getType() == Constants.ITEM && hasExpectedEntityType((Item) dso);
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public String getMIMEType() {
        return mimeType;
    }

    private List<TemplateLine> readTemplateLines(File templateFile) throws IOException, FileNotFoundException {
        try (BufferedReader templateReader = new BufferedReader(new FileReader(templateFile))) {
            return templateReader.lines()
                .map(this::buildTemplateLine)
                .collect(Collectors.toList());
        }
    }

    private TemplateLine buildTemplateLine(String templateLine) {

        Matcher matcher = FIELD_PATTERN.matcher(templateLine);
        if (!matcher.find()) {
            return new TemplateLine(templateLine);
        }

        String beforeField = templateLine.substring(0, matcher.start());
        String afterField = templateLine.substring(matcher.end());
        String field = templateLine.substring(matcher.start() + 1, matcher.end() - 1);

        TemplateLine templateLineObj = new TemplateLine(beforeField, afterField, field);
        if (templateLineObj.isVirtualField()) {
            String virtualFieldName = templateLineObj.getVirtualFieldName();
            if (!virtualFieldMapper.contains(virtualFieldName)) {
                throw new IllegalStateException("Unknown virtual field found in the template '" + templateFileName
                    + "': " + virtualFieldName);
            }
        }

        if (templateLineObj.isIfGroupField()) {
            String conditionName = templateLineObj.getIfConditionName();
            if (!conditionEvaluatorMapper.contains(conditionName)) {
                throw new IllegalStateException("Unknown condition evaluator found in the template '" + templateFileName
                    + "': " + conditionName);
            }
        }

        return templateLineObj;
    }

    private List<String> getItemLines(Context context, DSpaceObject dso, boolean findRelatedItems)
        throws CrosswalkObjectNotSupported, IOException {

        if (dso.getType() != Constants.ITEM) {
            throw new CrosswalkObjectNotSupported("ReferCrosswalk can only crosswalk an Item.");
        }

        Item item = (Item) dso;

        List<String> lines = new ArrayList<String>();
        appendLines(context, item, templateLines.iterator(), lines, findRelatedItems, -1);

        return lines;
    }

    private List<String> getSingleItemLines(Context context, DSpaceObject dso, TemplateLine line)
        throws CrosswalkObjectNotSupported, IOException {

        return getItemLines(context, dso, this.findRelatedItems);
    }

    private void appendLines(Context context, Item item, Iterator<TemplateLine> iterator, List<String> lines,
        boolean findRelatedItems, int pos) throws IOException {

        while (iterator.hasNext()) {

            TemplateLine templateLine = iterator.next();

            if (templateLine.isMetadataGroupStartField()) {
                handleMetadataGroup(context, item, iterator, templateLine.getMetadataGroupFieldName(), lines);
                continue;
            }

            if (templateLine.isRelationGroupStartField()) {
                handleRelationGroup(context, item, iterator, templateLine.getRelationName(), lines,
                        findRelatedItems, -1);
                continue;
            }

            if (templateLine.isIfGroupStartField()) {
                handleIfGroup(context, item, iterator, templateLine, lines, findRelatedItems, -1);
                continue;
            }

            if (StringUtils.isBlank(templateLine.getField())) {
                lines.add(templateLine.getBeforeField());
                continue;
            }

            List<String> metadataValues = getMetadataValuesForLine(context, templateLine, item);
            if (pos != -1) {
                if (pos < metadataValues.size()) {
                    metadataValues = List.of(metadataValues.get(pos));
                } else {
                    metadataValues = List.of(PLACEHOLDER_PARENT_METADATA_VALUE);
                }
            }
            for (String metadataValue : metadataValues) {
                if (isNotBlank(metadataValue)
                        && !StringUtils.equals(metadataValue, PLACEHOLDER_PARENT_METADATA_VALUE)) {
                    appendLine(lines, templateLine, metadataValue);
                }
            }
        }
    }

    private int getMetadataGroupSize(Item item, String metadataGroupFieldName)  {
        boolean isBundleGroup = metadataGroupFieldName.startsWith("bundle");
        if (isBundleGroup) {
            return bundleGroupSize(item, metadataGroupFieldName);
        }
        return itemService.getMetadataByMetadataString(item, metadataGroupFieldName).size();
    }

    private int bundleGroupSize(Item item, String metadataGroupFieldName) {
        String bundleName = StringUtils.stripStart(metadataGroupFieldName, "bundle.").toUpperCase();
        try {
            return itemService.getBundles(item, bundleName).stream()
                              .mapToInt(b -> b.getBitstreams().size())
                              .sum();
        } catch (SQLException e) {
            log.warn("error while extracting bitstreams size {}", e.getMessage());
            return 0;
        }
    }

    private List<String> getMetadataValuesForLine(Context context, TemplateLine line, Item item) {

        if (line.isVirtualField()) {
            VirtualField virtualField = virtualFieldMapper.getVirtualField(line.getVirtualFieldName());
            String[] values = virtualField.getMetadata(context, item, line.getField());
            return values != null ? Arrays.asList(values) : Collections.emptyList();
        }

        return metadataSecurityService
                .getPermissionFilteredMetadataValues(context, item, line.getField(), fastMetadataSecurity).stream()
            .map(MetadataValue::getValue)
            .collect(Collectors.toList());

    }

    private void handleMetadataGroup(Context context, Item item, Iterator<TemplateLine> iterator, String groupName,
        List<String> lines) throws IOException {

        List<TemplateLine> groupLines = getGroupLines(iterator, line -> line.isMetadataGroupEndField());
        int groupSize = getMetadataGroupSize(item, groupName);
        Map<String, List<String>> metadataValues = new HashMap<>();

        for (int i = 0; i < groupSize; i++) {
            Iterator<TemplateLine> groupLinesIter = groupLines.iterator();
            while (groupLinesIter.hasNext()) {
                TemplateLine line = groupLinesIter.next();
// we don't need to support group of groups at this time as we haven't nested of nested
//                if (line.isMetadataGroupStartField()) {
//                    handleMetadataGroup(context, item, groupLinesIter, line.getMetadataGroupFieldName(), lines, i);
//                    continue;
//                }

                if (line.isRelationGroupStartField()) {
                    handleRelationGroup(context, item, groupLinesIter, line.getRelationName(), lines, true, i);
                    continue;
                }

                if (line.isIfGroupStartField()) {
                    handleIfGroup(context, item, groupLinesIter, line, lines, false, i);
                    continue;
                }

                String field = line.getField();

                if (StringUtils.isBlank(line.getField())) {
                    lines.add(line.getBeforeField());
                    continue;
                }

                List<String> metadata = null;
                if (metadataValues.containsKey(field)) {
                    metadata = metadataValues.get(field);
                } else {
                    metadata = getMetadataValuesForLine(context, line, item);
                    metadataValues.put(field, metadata);
                }

                if (metadata.size() <= i) {
                    if (metadata.size() == 0) {
                        // if the group definition has been extended it is quite common that some "nested" metadata
                        // are completely missing
                        log.debug("The metadata group " + groupName + " for item with id "
                            + item.getID() + " miss the field " + field);
                    } else {
                        log.warn("The cardinality of metadata group " + groupName + " for the field "
                                + field + " is inconsistent for item with id "
                                + item.getID());
                    }
                    continue;
                }

                String metadataValue = metadata.get(i);
                if (isNotBlank(metadataValue) && !PLACEHOLDER_PARENT_METADATA_VALUE.equals(metadataValue)) {
                    appendLine(lines, line, metadataValue);
                }
            }
        }

    }

    private void handleRelationGroup(Context context, Item item, Iterator<TemplateLine> iterator, String relationName,
        List<String> lines, boolean findRelatedItems, int pos) throws IOException {

        List<TemplateLine> groupLines = getGroupLines(iterator, line -> line.isRelationGroupEndField(relationName));

        if (!findRelatedItems) {
            return;
        }

        Iterator<Item> relatedItems = findRelatedItems(context, item, relationName, pos);

        int relatedItemPos = -1;
        while (relatedItems.hasNext()) {
            Item relatedItem = relatedItems.next();
            Iterator<TemplateLine> lineIterator = groupLines.iterator();
            appendLines(context, relatedItem, lineIterator, lines, findRelatedItems, relatedItemPos);
            relatedItemPos++;
        }

    }

    private void handleIfGroup(Context context, Item item, Iterator<TemplateLine> iterator, TemplateLine conditionLine,
        List<String> lines, boolean findRelatedItems, int pos) throws IOException {

        String condition = conditionLine.getIfCondition();
        String conditionName = conditionLine.getIfConditionName();

        List<TemplateLine> groupLines = getGroupLines(iterator, line -> line.isIfGroupEndField(condition));

        ConditionEvaluator evaluator = conditionEvaluatorMapper.getConditionEvaluator(conditionName);
        if (evaluator.test(context, item, condition, pos)) {
            appendLines(context, item, groupLines.iterator(), lines, findRelatedItems, pos);
        }

    }

    private List<TemplateLine> getGroupLines(Iterator<TemplateLine> iterator, Predicate<TemplateLine> breakPredicate) {
        List<TemplateLine> templateLines = new ArrayList<TemplateLine>();
        while (iterator.hasNext()) {
            TemplateLine line = iterator.next();
            if (breakPredicate.test(line)) {
                break;
            }
            templateLines.add(line);
        }
        return templateLines;
    }

    private Iterator<Item> findRelatedItems(Context context, Item item, String relationName, int pos) {

        if (isMetadataField(relationName)) {
            return findByAuthorities(context, item, relationName, pos);
        }

        return searchConfigurationUtilsService.findByRelation(context, item, relationName);
    }

    private boolean isMetadataField(String relationName) {
        return relationName.contains("-");
    }

    private Iterator<Item> findByAuthorities(Context context, Item item, String metadataField, int pos) {
        if (pos == -1) {
            return itemService.getMetadataByMetadataString(item, metadataField.replaceAll("-", ".")).stream()
                .map(MetadataValue::getAuthority)
                .filter(Objects::nonNull)
                .filter(authority -> UUIDUtils.fromString(authority) != null)
                .map(authority -> findById(context, UUIDUtils.fromString(authority)))
                .filter(Objects::nonNull)
                .iterator();
        } else {
            return itemService.getMetadataByMetadataString(item, metadataField.replaceAll("-", ".")).stream()
                    .skip(pos)
                    .limit(1)
                    .map(MetadataValue::getAuthority)
                    .filter(Objects::nonNull)
                    .filter(authority -> UUIDUtils.fromString(authority) != null)
                    .map(authority -> findById(context, UUIDUtils.fromString(authority)))
                    .filter(Objects::nonNull)
                    .iterator();
        }
    }

    private void appendLine(List<String> lines, TemplateLine line, String value) {
        String valueToAdd = converter != null ? converter.convert(value) : value;
        lines.add(line.getBeforeField() + valueToAdd + line.getAfterField());
    }

    private void writeLines(BufferedWriter writer, List<String> lines) throws IOException {
        if (linesPostProcessor != null) {
            linesPostProcessor.accept(lines);
        }
        lines.stream().limit(lines.size() - 1).forEachOrdered(line -> {
            try {
                writer.write(line);
                writer.newLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        writer.write(lines.get(lines.size() - 1));
    }

    private Item findById(Context context, UUID id) {
        try {
            return itemService.find(context, id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hasExpectedEntityType(Item item) {
        String itemEntityType = itemService.getMetadataFirstValue(item, "dspace", "entity", "type", Item.ANY);
        return StringUtils.isEmpty(entityType) || Objects.equals("all", entityType)
            || Objects.equals(itemEntityType, entityType);
    }

    private boolean isMemberOfGroupNamed(Context context, EPerson ePerson, String groupName) {
        try {
            Group group = groupService.findByName(context, groupName);
            return groupService.isMember(context, ePerson, group);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setConverter(Converter<String, String> converter) {
        this.converter = converter;
    }

    public void setLinesPostProcessor(Consumer<List<String>> linesPostProcessor) {
        this.linesPostProcessor = linesPostProcessor;
    }

    public void setMultipleItemsTemplateFileName(String multipleItemsTemplateFileName) {
        this.multipleItemsTemplateFileName = multipleItemsTemplateFileName;
    }

    public String getTemplateFileName() {
        return templateFileName;
    }

    public void setTemplateFileName(String templateFileName) {
        this.templateFileName = templateFileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    @Override
    public Optional<String> getEntityType() {
        return Optional.ofNullable(entityType);
    }

    public void setCrosswalkMode(CrosswalkMode crosswalkMode) {
        this.crosswalkMode = crosswalkMode;
    }

    @Override
    public CrosswalkMode getCrosswalkMode() {
        return Optional.ofNullable(this.crosswalkMode).orElse(ItemExportCrosswalk.super.getCrosswalkMode());
    }

    @Override
    public boolean isPubliclyReadable() {
        return this.publiclyReadable;
    }

    public void setPubliclyReadable(boolean isPubliclyReadable) {
        this.publiclyReadable = isPubliclyReadable;
    }

    public List<String> getAllowedGroups() {
        return allowedGroups;
    }

    public void setAllowedGroups(List<String> allowedGroups) {
        this.allowedGroups = allowedGroups;
    }

    public void setFindRelatedItems(boolean findRelatedItems) {
        this.findRelatedItems = findRelatedItems;
    }

    public void setFastMetadataSecurity(boolean fastMetadataSecurity) {
        this.fastMetadataSecurity = fastMetadataSecurity;
    }
}
