/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Implementation of {@StreamDisseminationCrosswalk} to produce a xls file starting from a template.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public class XlsCrosswalk extends TabularCrosswalk {

	public static String CELL_CONTAINS_TRUNCATED = "!CELL CONTENT WAS TRUNCATED DURING EXPORT! ";
	public static String COLUMN_CONTAINS_TRUNCATED = "!COLUMN CONTAINS TRUNCATED CELL(S)! ";
    private String sheetName;

    @Override
    public String getMIMEType() {
        return "application/vnd.ms-excel";
    }

    @Override
    protected void writeRows(List<List<String>> rows, OutputStream out) {
        try (Workbook workbook = new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName);

            for (int i = 0; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                Row sheetRow = sheet.createRow(i);

                for (int j = 0; j < row.size(); j++) {
                    String field = row.get(j);
                    Cell cell = sheetRow.createCell(j);

                    if (StringUtils.length(field) > 32726) {
                        cell.setCellValue(getTruncatedCellPrefix() + field.substring(0, 32726 - 43 - 1) + "…");
                        Cell headerCell = sheet.getRow(0).getCell(j);

                        if (!headerCell.getStringCellValue().startsWith(getTruncatedHeaderPrefix())) {
                            headerCell.setCellValue(getTruncatedHeaderPrefix() + headerCell.getStringCellValue());
                        }
                    } else {
                        cell.setCellValue(field);
                    }
                }
            }

            autoSizeColumns(sheet);
            workbook.write(out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void autoSizeColumns(Sheet sheet) {
        if (sheet.getPhysicalNumberOfRows() > 0) {
            sheet.getRow(sheet.getFirstRowNum()).forEach(cell -> sheet.autoSizeColumn(cell.getColumnIndex()));
        }
    }

    protected String getValuesSeparator() {
        return configurationService.getProperty("crosswalk.xls.separator.values", "||");
    }

    protected String getNestedValuesSeparator() {
        return configurationService.getProperty("crosswalk.xls.separator.nested-values", "||");
    }

    protected String getInsideNestedSeparator() {
        return configurationService.getProperty("crosswalk.xls.separator.inside-nested", "/");
    }

    protected String getTruncatedCellPrefix() {
        return configurationService.getProperty(
            "crosswalk.xls.truncated-prefix.cell",
            CELL_CONTAINS_TRUNCATED
        );
    }

    protected String getTruncatedHeaderPrefix() {
        return configurationService.getProperty(
            "crosswalk.xls.truncated-prefix.header",
            COLUMN_CONTAINS_TRUNCATED
        );
    }

    protected String escapeValue(String value) {
        return value;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

}
