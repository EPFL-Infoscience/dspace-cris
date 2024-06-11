<?xml version="1.0" encoding="UTF-8" ?>
<!-- http://www.loc.gov/marc/bibliographic/ecbdlist.html -->
<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:doc="http://www.lyncode.com/xoai"
    version="1.0">
    <xsl:output omit-xml-declaration="yes" method="xml" indent="yes" />
    <xsl:template match="/">
    <xsl:variable name="entityType" select="doc:metadata/doc:element[@name='dspace']/doc:element[@name='entity']/doc:element[@name='type']/doc:element/doc:field[@name='value']"/>
    <xsl:choose>
        <xsl:when test="$entityType='Publication'">
            <xsl:for-each select="doc:metadata/doc:element[@name='oaiopenaire']/doc:element[@name='publication']/doc:element[@name='none']/doc:field[@name='value']">
                <xsl:value-of select="." disable-output-escaping="yes"/>
            </xsl:for-each>
        </xsl:when>
        <xsl:when test="$entityType='Product'">    
        <xsl:for-each select="doc:metadata/doc:element[@name='oaiopenaire']/doc:element[@name='product']/doc:element[@name='none']/doc:field[@name='value']">
            <xsl:value-of select="." disable-output-escaping="yes"/>
        </xsl:for-each>
        </xsl:when>
        <xsl:when test="$entityType='Patent'">
        <xsl:for-each select="doc:metadata/doc:element[@name='oaiopenaire']/doc:element[@name='patent']/doc:element[@name='none']/doc:field[@name='value']">
            <xsl:value-of select="." disable-output-escaping="yes"/>
        </xsl:for-each>
        </xsl:when>
    </xsl:choose>    
    </xsl:template>
</xsl:stylesheet>
