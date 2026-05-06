/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks.csl;

import static org.dspace.app.itemupdate.MetadataUtilities.parseCompoundForm;
import static org.dspace.content.Item.ANY;

import java.text.ParseException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.google.gson.GsonBuilder;
import de.undercouch.citeproc.ListItemDataProvider;
import de.undercouch.citeproc.csl.CSLDate;
import de.undercouch.citeproc.csl.CSLDateBuilder;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import de.undercouch.citeproc.csl.CSLName;
import de.undercouch.citeproc.csl.CSLType;
import de.undercouch.citeproc.helper.json.JsonBuilder;
import de.undercouch.citeproc.helper.json.MapJsonBuilderFactory;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.content.DCDate;
import org.dspace.content.DCPersonName;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.util.SimpleMapConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link ListItemDataProvider} to provide {@link CSLItemData}
 * starting from a DSpace Item.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public class DSpaceListItemDataProvider extends ListItemDataProvider {

    public static final Logger LOGGER = LoggerFactory.getLogger(DSpaceListItemDataProvider.class);

    private final String DEFAULT_TYPE = "default";

    private final ItemService itemService;

    private String citationLanguage;

    private String id;
    private String type;
    private String categories;
    private String language;
    private Map<String, String> journalAbbreviation;
    private String shortTitle;
    private String author;
    private String collectionEditor;
    private String composer;
    private String containerAuthor;
    private String director;
    private String editor;
    private String editorialDirector;
    private String interviewer;
    private String illustrator;
    private String originalAuthor;
    private String recipient;
    private String reviewedAuthor;
    private String translator;
    private String accessed;
    private String container;
    private String eventDate;
    private String issued;
    private String originalDate;
    private String submitted;
    private String abstrct;
    private String annote;
    private String archive;
    private String archiveLocation;
    private String archivePlace;
    private String authority;
    private String callNumber;
    private String chapterNumber;
    private String citationNumber;
    private String citationLabel;
    private String collectionNumber;
    private String collectionTitle;
    private CascadeMetadataRule containerTitle;
    private String containerTitleShort;
    private String dimensions;
    private CascadeMetadataRule DOI;
    private String edition;
    private String event;
    private String eventPlace;
    private String firstReferenceNoteNumber;
    private MetadataMergeRuleWithMapping genre;
    private CascadeMetadataRule ISBN;
    private CascadeMetadataRule ISSN;
    private String issue;
    private String jurisdiction;
    private String keyword;
    private String locator;
    private String medium;
    private String note;
    private CascadeMetadataRule number;
    private String numberOfPages;
    private String numberOfVolumes;
    private String originalPublisher;
    private String originalPublisherPlace;
    private String originalTitle;
    private MetadataMergeRuleWithMapping page;
    private String pageFirst;
    private String PMCID;
    private String PMID;
    private String publisher;
    private String publisherPlace;
    private String references;
    private String reviewedTitle;
    private String scale;
    private String section;
    private String source;
    private String status;
    private String title;
    private String titleShort;
    private CascadeMetadataRule URL;
    private String version;
    private String volume;
    private String yearSuffix;

    private SimpleMapConverter typeConverter;

    public DSpaceListItemDataProvider(ItemService itemService) {
        this.itemService = itemService;
    }

    public void processItem(Item item) {
        CSLItemDataBuilder itemBuilder = new CSLItemDataBuilder();
        itemBuilder.id(String.valueOf(item.getID()));

        handleStringFields(item, itemBuilder);
        handleCslNameFields(item, itemBuilder);
        handleCslDateFields(item, itemBuilder);
        itemBuilder.citationKey("item_" + item.getID().toString().replace("-", ""));
        // citeproc-server still doesn't understand the citation key
        // https://github.com/citation-style-language/styles/pull/5117
        itemBuilder.citationLabel("item_" + item.getID().toString().replace("-", ""));
        CSLItemData cslItemData = itemBuilder.build();
        this.items.put(cslItemData.getId(), cslItemData);
    }

    public void processItems(Iterator<Item> items) {
        while (items.hasNext()) {
            processItem(items.next());
        }
    }

    public String toJson() {
        JsonBuilder jsonBuilder = new MapJsonBuilderFactory().createJsonBuilder();
        jsonBuilder.add("items", jsonBuilder.toJson(this.items.values()));
        return prettyPrint(jsonBuilder.build());
    }

    protected CSLItemDataBuilder handleStringFields(Item item, CSLItemDataBuilder itemBuilder) {
        String typeValue = getTypeValue(type, item);

        CSLType cslType = getPublicationType(getMetadataFirstValue(item, type));
        consumeMetadataIfNotBlank(type, item, value -> itemBuilder.type(getPublicationType(value)));
        consumeIfNotBlank(categories, value -> itemBuilder.categories(getMetadataValues(item, value)));
        consumeMetadataIfNotBlank(language, item, value -> itemBuilder.language(value));
        //consumeMetadataByTypeIfNotBlank(journalAbbreviation, item, typeValue,
        //    value -> itemBuilder.journalAbbreviation(value));
        consumeMetadataIfNotBlank(shortTitle, item, value -> itemBuilder.shortTitle(value));
        consumeMetadataIfNotBlank(abstrct, item, value -> itemBuilder.abstrct(value));
        consumeMetadataIfNotBlank(annote, item, value -> itemBuilder.annote(value));
        consumeMetadataIfNotBlank(archive, item, value -> itemBuilder.archive(value));
        consumeMetadataIfNotBlank(archiveLocation, item, value -> itemBuilder.archiveLocation(value));
        consumeMetadataIfNotBlank(archivePlace, item, value -> itemBuilder.archivePlace(value));
        consumeMetadataIfNotBlank(authority, item, value -> itemBuilder.authority(value));
        consumeMetadataIfNotBlank(callNumber, item, value -> itemBuilder.callNumber(value));
        consumeMetadataIfNotBlank(chapterNumber, item, value -> itemBuilder.chapterNumber(value));
        consumeMetadataIfNotBlank(citationNumber, item, value -> itemBuilder.citationNumber(value));
        consumeMetadataIfNotBlank(citationLabel, item, value -> itemBuilder.citationLabel(value));
        consumeMetadataIfNotBlank(collectionNumber, item, value -> itemBuilder.collectionNumber(value));
        consumeMetadataIfNotBlank(collectionTitle, item, value -> itemBuilder.collectionTitle(value));
        consumeMetadataValueIfNotBlank(() -> containerTitle != null ? containerTitle.getValue(item) : null, item,
                value -> itemBuilder.containerTitle(value.get()));
        consumeMetadataIfNotBlank(containerTitleShort, item, value -> itemBuilder.containerTitleShort(value));
        consumeMetadataIfNotBlank(dimensions, item, value -> itemBuilder.dimensions(value));
        consumeMetadataValueIfNotBlank(() -> DOI != null ? DOI.getValue(item) : null, item,
                value -> itemBuilder.DOI(value.get()));
        consumeMetadataIfNotBlank(edition, item, value -> itemBuilder.edition(value));
        consumeMetadataIfNotBlank(event, item, value -> itemBuilder.event(value));
        consumeMetadataIfNotBlank(eventPlace, item, value -> itemBuilder.eventPlace(value));
        consumeMetadataIfNotBlank(firstReferenceNoteNumber, item,
                value -> itemBuilder.firstReferenceNoteNumber(value));
        if (cslType != null) {
            consumeMetadataValueIfNotBlank(() -> genre != null ?
                            genre.getValue(item, cslType.toString()) : null, item,
                    value -> itemBuilder.genre(value.get()));
            consumeMetadataIfNotBlank(publisher, item, value -> itemBuilder.publisher(value));
            setPageValues(page != null ? page.getValue(item, cslType.toString()) : null, item, itemBuilder);
        }
        consumeMetadataValueIfNotBlank(() -> ISBN != null ? ISBN.getValue(item) : null, item,
                value -> itemBuilder.ISBN(value.get()));
        consumeMetadataValueIfNotBlank(() -> ISSN != null ? ISSN.getValue(item) : null, item,
                value -> itemBuilder.ISSN(value.get()));
        consumeMetadataIfNotBlank(issue, item, value -> itemBuilder.issue(value));
        consumeMetadataIfNotBlank(jurisdiction, item, value -> itemBuilder.jurisdiction(value));
        consumeMetadataValuesIfNotBlank(keyword, item, values -> itemBuilder.keyword(String.join(" | ", values)));
        consumeMetadataIfNotBlank(locator, item, value -> itemBuilder.locator(value));
        consumeMetadataIfNotBlank(medium, item, value -> itemBuilder.medium(value));
        consumeMetadataIfNotBlank(note, item, value -> itemBuilder.note(value));
        consumeMetadataIfNotBlank(number != null ? number.getValue(item) : null, item,
                value -> itemBuilder.number(value));
        consumeMetadataIfNotBlank(numberOfPages, item, value -> itemBuilder.numberOfPages(value));
        consumeMetadataIfNotBlank(numberOfVolumes, item, value -> itemBuilder.numberOfVolumes(value));
        consumeMetadataIfNotBlank(originalPublisher, item, value -> itemBuilder.originalPublisher(value));
        consumeMetadataIfNotBlank(originalPublisherPlace, item, value -> itemBuilder.originalPublisherPlace(value));
        consumeMetadataIfNotBlank(originalTitle, item, value -> itemBuilder.originalTitle(value));
        consumeMetadataIfNotBlank(PMCID, item, value -> itemBuilder.PMCID(value));
        consumeMetadataIfNotBlank(PMID, item, value -> itemBuilder.PMID(value));
        consumeMetadataIfNotBlank(publisherPlace, item, value -> itemBuilder.publisherPlace(value));
        consumeMetadataIfNotBlank(references, item, value -> itemBuilder.references(value));
        consumeMetadataIfNotBlank(reviewedTitle, item, value -> itemBuilder.reviewedTitle(value));
        consumeMetadataIfNotBlank(scale, item, value -> itemBuilder.scale(value));
        consumeMetadataIfNotBlank(section, item, value -> itemBuilder.section(value));
        consumeMetadataIfNotBlank(source, item, value -> itemBuilder.source(value));
        consumeMetadataIfNotBlank(status, item, value -> itemBuilder.status(value));
        consumeMetadataIfNotBlank(title, item, value -> itemBuilder.title(value));
        consumeMetadataIfNotBlank(titleShort, item, value -> itemBuilder.titleShort(value));
        consumeMetadataValueIfNotBlank(() -> URL != null ? URL.getValue(item) : null, item,
                value -> itemBuilder.URL(value.get()));
        consumeMetadataIfNotBlank(version, item, value -> itemBuilder.version(value));
        consumeMetadataIfNotBlank(volume, item, value -> itemBuilder.volume(value));
        consumeMetadataIfNotBlank(yearSuffix, item, value -> itemBuilder.yearSuffix(value));

        return itemBuilder;
    }

    private void setPageValues(String page, Item item, CSLItemDataBuilder itemBuilder) {
        if (StringUtils.isBlank(page)) {
            return;
        }

        String metadataFirstValue = getMetadataFirstValue(item, page);
        if (StringUtils.isNotBlank(metadataFirstValue)) {
            String[] pageParts = metadataFirstValue.split(Pattern.quote("-"));
            if (pageParts.length == 2) {
                itemBuilder.page(pageParts[0].trim(), pageParts[1].trim());
            } else {
                LOGGER.warn("Invalid page format: '{}'. Expected format is 'page' or 'pageFirst-page'.", page,
                        metadataFirstValue);
            }
        }
    }

    protected CSLItemDataBuilder handleCslNameFields(Item item, CSLItemDataBuilder itemBuilder) {

        consumeCSLNamesIfNotBlank(author, item, names -> itemBuilder.author(names));
        consumeCSLNamesIfNotBlank(collectionEditor, item, names -> itemBuilder.collectionEditor(names));
        consumeCSLNamesIfNotBlank(composer, item, names -> itemBuilder.composer(names));
        consumeCSLNamesIfNotBlank(containerAuthor, item, names -> itemBuilder.containerAuthor(names));
        consumeCSLNamesIfNotBlank(director, item, names -> itemBuilder.director(names));
        consumeCSLNamesIfNotBlank(editor, item, names -> itemBuilder.editor(names));
        consumeCSLNamesIfNotBlank(editorialDirector, item, names -> itemBuilder.editorialDirector(names));
        consumeCSLNamesIfNotBlank(interviewer, item, names -> itemBuilder.interviewer(names));
        consumeCSLNamesIfNotBlank(illustrator, item, names -> itemBuilder.illustrator(names));
        consumeCSLNamesIfNotBlank(originalAuthor, item, names -> itemBuilder.originalAuthor(names));
        consumeCSLNamesIfNotBlank(recipient, item, names -> itemBuilder.recipient(names));
        consumeCSLNamesIfNotBlank(reviewedAuthor, item, names -> itemBuilder.reviewedAuthor(names));
        consumeCSLNamesIfNotBlank(translator, item, names -> itemBuilder.translator(names));

        return itemBuilder;
    }

    protected CSLItemDataBuilder handleCslDateFields(Item item, CSLItemDataBuilder itemBuilder) {

        consumeDateIfNotBlank(accessed, item, itemBuilder::accessed);
        consumeDateIfNotBlank(container, item, itemBuilder::container);
        consumeDateIfNotBlank(eventDate, item, itemBuilder::eventDate);
        consumeDateIfNotBlank(issued, item, itemBuilder::issued);
        consumeDateIfNotBlank(originalDate, item, itemBuilder::originalDate);
        consumeDateIfNotBlank(submitted, item, itemBuilder::submitted);

        return itemBuilder;
    }

    protected DCDate getDCDateFromMetadataValue(Item item, String metadataField) {
        String[] mdf = parseMetadataField(metadataField);
        return new DCDate(itemService.getMetadataFirstValue(item, mdf[0], mdf[1], mdf.length > 2 ? mdf[2] : null, ANY));
    }

    protected String getMetadataFirstValue(Item item, String metadataField) {
        String[] mdf = parseMetadataField(metadataField);
        return itemService.getMetadataFirstValue(item, mdf[0], mdf[1], mdf.length > 2 ? mdf[2] : null, Item.ANY);
    }

    protected CSLName[] getCslNameFromMetadataValue(Item item, String metadataField) {
        String[] mdf = parseMetadataField(metadataField);
        CSLName[] names = itemService.getMetadata(item, mdf[0], mdf[1], mdf.length > 2 ? mdf[2] : null, ANY).stream()
            .map(metadata -> new DCPersonName(metadata.getValue()))
            .map(name -> toCSLName(name))
            .toArray(CSLName[]::new);
        // If no names are found, return a null value this is important for CSL processing
        if (names.length == 0) {
            return null;
        }
        return names;
    }

    private CSLName toCSLName(DCPersonName name) {
        String lastName = StringUtils.isNotBlank(name.getLastName()) ? name.getLastName() : null;
        String firstName = StringUtils.isNotBlank(name.getFirstNames()) ? name.getFirstNames() : null;
        return new CSLName(lastName, firstName, null, null, null, null, null, null, null, null, null, null);
    }

    protected String[] getMetadataValues(Item item, String metadataField) {
        String[] mdf = parseMetadataField(metadataField);
        return itemService.getMetadata(item, mdf[0], mdf[1], mdf.length > 2 ? mdf[2] : null, ANY).stream()
            .map(MetadataValue::getValue)
            .toArray(String[]::new);
    }

    private String prettyPrint(Object json) {
        return new GsonBuilder().registerTypeAdapter(CSLType.class, new CSLTypeAdapter())
                .setPrettyPrinting().create().toJson(json);
    }

    private String[] parseMetadataField(String metadataField) {
        try {
            return parseCompoundForm(metadataField);
        } catch (ParseException e) {
            throw new RuntimeException("An error occurs parsing the metadata '" + metadataField + "'", e);
        }
    }

    private void consumeIfNotBlank(String value, Consumer<String> consumer) {
        if (StringUtils.isNotBlank(value)) {
            consumer.accept(value);
        }
    }

    private void consumeMetadataIfNotBlank(String value, Item item, Consumer<String> consumer) {
        if (StringUtils.isNotBlank(value)) {
            String metadataFirstValue = getMetadataFirstValue(item, value);
            if (StringUtils.isNotBlank(metadataFirstValue)) {
                consumer.accept(metadataFirstValue);
            }
        }
    }

    private String getTypeValue(String type, Item item) {
        String typeValue = null;
        if (StringUtils.isNotBlank(type)) {
            String metadataFirstValue = getMetadataFirstValue(item, type);
            if (StringUtils.isNotBlank(metadataFirstValue)) {
                CSLType cslType = getPublicationType(metadataFirstValue);
                if (cslType != null) {
                    typeValue = cslType.toString();
                }
            }
        }
        return typeValue;
    }

    private void consumeMetadataByTypeIfNotBlank(Map<String, String> mapConfig, Item item,
                                                 String typeValue, Consumer<String> consumer) {
        if (StringUtils.isNotBlank(typeValue)) {
            String value = mapConfig.get(typeValue);
            if (StringUtils.isBlank(value)) {
                value = mapConfig.get(DEFAULT_TYPE);
            }
            if (StringUtils.isNotBlank(value)) {
                String metadataFirstValue = getMetadataFirstValue(item, value);
                if (StringUtils.isNotBlank(metadataFirstValue)) {
                    consumer.accept(metadataFirstValue);
                }
            }
        }
    }

    private void consumePageMetadataIfNotBlank(String firstPage, String secondPage,
                                               Item item, Consumer<String> consumer) {
        if (StringUtils.isNotBlank(firstPage) && StringUtils.isNotBlank(secondPage)) {
            String metadataFirstPageValue = getMetadataFirstValue(item, firstPage);
            String metadataSecondPageValue = getMetadataFirstValue(item, secondPage);
            if (StringUtils.isNotBlank(metadataFirstPageValue) && StringUtils.isNotBlank(metadataSecondPageValue)) {
                consumer.accept(metadataFirstPageValue + "-" + metadataSecondPageValue);
            }
        } else if (StringUtils.isNotBlank(firstPage)) {
            String value = getMetadataFirstValue(item, firstPage);
            if (StringUtils.isNotBlank(value)) {
                consumer.accept(value);
            }
        } else if (StringUtils.isNotBlank(secondPage)) {
            String value = getMetadataFirstValue(item, secondPage);
            if (StringUtils.isNotBlank(value)) {
                consumer.accept(value);
            }
        }
    }

    private void consumeMetadataValuesIfNotBlank(String value, Item item, Consumer<String[]> consumer) {
        if (StringUtils.isNotBlank(value)) {
            String[] metadataValues = getMetadataValues(item, value);
            if (metadataValues.length > 0) {
                consumer.accept(metadataValues);
            }
        }
    }

    private void consumeMetadataValueIfNotBlank(Supplier<String> valueSupplier, Item item,
                                           Consumer<Supplier<String>> consumer) {
        String value = valueSupplier.get();
        if (StringUtils.isNotBlank(value)) {
            consumer.accept(() -> value);
        }
    }

    private void consumeCSLNamesIfNotBlank(String value, Item item, Consumer<CSLName[]> consumer) {
        if (StringUtils.isNotBlank(value)) {
            consumer.accept(getCslNameFromMetadataValue(item, value));
        }
    }

    private void consumeDateIfNotBlank(String value, Item item, Consumer<CSLDate> consumer) {

        if (StringUtils.isBlank(value)) {
            return;
        }

        DCDate dcDate = getDCDateFromMetadataValue(item, value);

        convertToCSLDate(dcDate).ifPresent(consumer::accept);

    }

    private Optional<CSLDate> convertToCSLDate(DCDate dcDate) {

        if (dcDate.toDate() == null || dcDate.getYear() == -1) {
            return Optional.empty();
        }

        int[] dateParts = new int[] { dcDate.getYear() };

        int month = dcDate.getMonth();
        if (month != -1) {
            dateParts = ArrayUtils.add(dateParts, month);

            int day = dcDate.getDay();
            if (day != -1) {
                dateParts = ArrayUtils.add(dateParts, day);
            }

        }

        return Optional.of(new CSLDateBuilder().dateParts(dateParts).build());

    }

    private CSLType getPublicationType(String value) {
        try {
            return CSLType.fromString(typeConverter.getValue(value));
        } catch (Exception ex) {
            LOGGER.warn("No CSL type found by type: " + value);
            return null;
        }
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getCategories() {
        return categories;
    }

    public String getLanguage() {
        return language;
    }

    public Map<String, String> getJournalAbbreviation() {
        return journalAbbreviation;
    }

    public String getShortTitle() {
        return shortTitle;
    }

    public String getAuthor() {
        return author;
    }

    public String getCollectionEditor() {
        return collectionEditor;
    }

    public String getComposer() {
        return composer;
    }

    public String getContainerAuthor() {
        return containerAuthor;
    }

    public String getDirector() {
        return director;
    }

    public String getEditor() {
        return editor;
    }

    public String getEditorialDirector() {
        return editorialDirector;
    }

    public String getInterviewer() {
        return interviewer;
    }

    public String getIllustrator() {
        return illustrator;
    }

    public String getOriginalAuthor() {
        return originalAuthor;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getReviewedAuthor() {
        return reviewedAuthor;
    }

    public String getTranslator() {
        return translator;
    }

    public String getAccessed() {
        return accessed;
    }

    public String getContainer() {
        return container;
    }

    public String getEventDate() {
        return eventDate;
    }

    public String getIssued() {
        return issued;
    }

    public String getOriginalDate() {
        return originalDate;
    }

    public String getSubmitted() {
        return submitted;
    }

    public String getAbstrct() {
        return abstrct;
    }

    public String getAnnote() {
        return annote;
    }

    public String getArchive() {
        return archive;
    }

    public String getArchiveLocation() {
        return archiveLocation;
    }

    public String getArchivePlace() {
        return archivePlace;
    }

    public String getAuthority() {
        return authority;
    }

    public String getCallNumber() {
        return callNumber;
    }

    public String getChapterNumber() {
        return chapterNumber;
    }

    public String getCitationNumber() {
        return citationNumber;
    }

    public String getCitationLabel() {
        return citationLabel;
    }

    public String getCollectionNumber() {
        return collectionNumber;
    }

    public String getCollectionTitle() {
        return collectionTitle;
    }

    public CascadeMetadataRule getContainerTitle() {
        return containerTitle;
    }

    public String getContainerTitleShort() {
        return containerTitleShort;
    }

    public String getDimensions() {
        return dimensions;
    }

    public CascadeMetadataRule getDOI() {
        return DOI;
    }

    public String getEdition() {
        return edition;
    }

    public String getEvent() {
        return event;
    }

    public String getEventPlace() {
        return eventPlace;
    }

    public String getFirstReferenceNoteNumber() {
        return firstReferenceNoteNumber;
    }

    public MetadataMergeRuleWithMapping getGenre() {
        return genre;
    }

    public CascadeMetadataRule getISBN() {
        return ISBN;
    }

    public CascadeMetadataRule getISSN() {
        return ISSN;
    }

    public String getIssue() {
        return issue;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getLocator() {
        return locator;
    }

    public String getMedium() {
        return medium;
    }

    public String getNote() {
        return note;
    }

    public CascadeMetadataRule getNumber() {
        return number;
    }

    public String getNumberOfPages() {
        return numberOfPages;
    }

    public String getNumberOfVolumes() {
        return numberOfVolumes;
    }

    public String getOriginalPublisher() {
        return originalPublisher;
    }

    public String getOriginalPublisherPlace() {
        return originalPublisherPlace;
    }

    public String getOriginalTitle() {
        return originalTitle;
    }

    public MetadataMergeRuleWithMapping getPage() {
        return page;
    }

    public String getPageFirst() {
        return pageFirst;
    }

    public String getPMCID() {
        return PMCID;
    }

    public String getPMID() {
        return PMID;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getPublisherPlace() {
        return publisherPlace;
    }

    public String getReferences() {
        return references;
    }

    public String getReviewedTitle() {
        return reviewedTitle;
    }

    public String getScale() {
        return scale;
    }

    public String getSection() {
        return section;
    }

    public String getSource() {
        return source;
    }

    public String getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getTitleShort() {
        return titleShort;
    }

    public CascadeMetadataRule getURL() {
        return URL;
    }

    public String getVersion() {
        return version;
    }

    public String getVolume() {
        return volume;
    }

    public String getYearSuffix() {
        return yearSuffix;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setCategories(String categories) {
        this.categories = categories;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setJournalAbbreviation(Map<String, String> journalAbbreviation) {
        this.journalAbbreviation = journalAbbreviation;
    }

    public void setShortTitle(String shortTitle) {
        this.shortTitle = shortTitle;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setCollectionEditor(String collectionEditor) {
        this.collectionEditor = collectionEditor;
    }

    public void setComposer(String composer) {
        this.composer = composer;
    }

    public void setContainerAuthor(String containerAuthor) {
        this.containerAuthor = containerAuthor;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    public void setEditor(String editor) {
        this.editor = editor;
    }

    public void setEditorialDirector(String editorialDirector) {
        this.editorialDirector = editorialDirector;
    }

    public void setInterviewer(String interviewer) {
        this.interviewer = interviewer;
    }

    public void setIllustrator(String illustrator) {
        this.illustrator = illustrator;
    }

    public void setOriginalAuthor(String originalAuthor) {
        this.originalAuthor = originalAuthor;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public void setReviewedAuthor(String reviewedAuthor) {
        this.reviewedAuthor = reviewedAuthor;
    }

    public void setTranslator(String translator) {
        this.translator = translator;
    }

    public void setAccessed(String accessed) {
        this.accessed = accessed;
    }

    public void setContainer(String container) {
        this.container = container;
    }

    public void setEventDate(String eventDate) {
        this.eventDate = eventDate;
    }

    public void setIssued(String issued) {
        this.issued = issued;
    }

    public void setOriginalDate(String originalDate) {
        this.originalDate = originalDate;
    }

    public void setSubmitted(String submitted) {
        this.submitted = submitted;
    }

    public void setAbstrct(String abstrct) {
        this.abstrct = abstrct;
    }

    public void setAnnote(String annote) {
        this.annote = annote;
    }

    public void setArchive(String archive) {
        this.archive = archive;
    }

    public void setArchiveLocation(String archiveLocation) {
        this.archiveLocation = archiveLocation;
    }

    public void setArchivePlace(String archivePlace) {
        this.archivePlace = archivePlace;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public void setCallNumber(String callNumber) {
        this.callNumber = callNumber;
    }

    public void setChapterNumber(String chapterNumber) {
        this.chapterNumber = chapterNumber;
    }

    public void setCitationNumber(String citationNumber) {
        this.citationNumber = citationNumber;
    }

    public void setCitationLabel(String citationLabel) {
        this.citationLabel = citationLabel;
    }

    public void setCollectionNumber(String collectionNumber) {
        this.collectionNumber = collectionNumber;
    }

    public void setCollectionTitle(String collectionTitle) {
        this.collectionTitle = collectionTitle;
    }

    public void setContainerTitle(CascadeMetadataRule containerTitle) {
        this.containerTitle = containerTitle;
    }

    public void setContainerTitleShort(String containerTitleShort) {
        this.containerTitleShort = containerTitleShort;
    }

    public void setDimensions(String dimensions) {
        this.dimensions = dimensions;
    }

    public void setDOI(CascadeMetadataRule DOI) {
        this.DOI = DOI;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public void setEventPlace(String eventPlace) {
        this.eventPlace = eventPlace;
    }

    public void setFirstReferenceNoteNumber(String firstReferenceNoteNumber) {
        this.firstReferenceNoteNumber = firstReferenceNoteNumber;
    }

    public void setGenre(MetadataMergeRuleWithMapping genre) {
        this.genre = genre;
    }

    public void setISBN(CascadeMetadataRule ISBN) {
        this.ISBN = ISBN;
    }

    public void setISSN(CascadeMetadataRule ISSN) {
        this.ISSN = ISSN;
    }

    public void setIssue(String issue) {
        this.issue = issue;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public void setLocator(String locator) {
        this.locator = locator;
    }

    public void setMedium(String medium) {
        this.medium = medium;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public void setNumber(CascadeMetadataRule number) {
        this.number = number;
    }

    public void setNumberOfPages(String numberOfPages) {
        this.numberOfPages = numberOfPages;
    }

    public void setNumberOfVolumes(String numberOfVolumes) {
        this.numberOfVolumes = numberOfVolumes;
    }

    public void setOriginalPublisher(String originalPublisher) {
        this.originalPublisher = originalPublisher;
    }

    public void setOriginalPublisherPlace(String originalPublisherPlace) {
        this.originalPublisherPlace = originalPublisherPlace;
    }

    public void setOriginalTitle(String originalTitle) {
        this.originalTitle = originalTitle;
    }

    public void setPage(MetadataMergeRuleWithMapping page) {
        this.page = page;
    }

    public void setPageFirst(String pageFirst) {
        this.pageFirst = pageFirst;
    }

    public void setPMCID(String PMCID) {
        this.PMCID = PMCID;
    }

    public void setPMID(String PMID) {
        this.PMID = PMID;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public void setPublisherPlace(String publisherPlace) {
        this.publisherPlace = publisherPlace;
    }

    public void setReferences(String references) {
        this.references = references;
    }

    public void setReviewedTitle(String reviewedTitle) {
        this.reviewedTitle = reviewedTitle;
    }

    public void setScale(String scale) {
        this.scale = scale;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setTitleShort(String titleShort) {
        this.titleShort = titleShort;
    }

    public void setURL(CascadeMetadataRule URL) {
        this.URL = URL;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public void setYearSuffix(String yearSuffix) {
        this.yearSuffix = yearSuffix;
    }

    public void setTypeConverter(SimpleMapConverter typeConverter) {
        this.typeConverter = typeConverter;
    }

    public String getCitationLanguage() {
        if (StringUtils.isBlank(citationLanguage)) {
            return "en-US";
        }

        switch (citationLanguage) {
            case "fr":
            case "fr_FR":
                return "fr-FR";
            case "en":
            case "en_US":
            case "en_GB":
            default:
                return "en-US"; // Default to English if not specified
        }
    }

    public void setCitationLanguage(String citationLanguage) {
        this.citationLanguage = citationLanguage;
    }
}
