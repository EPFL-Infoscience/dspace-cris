<?xml version="1.0" encoding="UTF-8" ?>
<!-- http://www.loc.gov/marc/bibliographic/ecbdlist.html -->
<xsl:stylesheet 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:doc="http://www.lyncode.com/xoai"
	version="1.0">
	<xsl:output omit-xml-declaration="yes" method="xml" indent="yes" />
	
	<xsl:template match="/">
		<record xmlns="http://www.loc.gov/MARC21/slim" 
			xmlns:dcterms="http://purl.org/dc/terms/"
			xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
			xsi:schemaLocation="http://www.loc.gov/MARC21/slim http://www.loc.gov/standards/marcxml/schema/MARC21slim.xsd">
			<leader>00925njm 22002777a 4500</leader>
			<datafield ind2=" " ind1=" " tag="042">
				<subfield code="a">dc</subfield>
			</datafield>
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='type']/doc:element/doc:field[@name='authority']">
				<datafield ind2=" " ind1=" " tag="336">
					<subfield code="a">http://purl.org/coar/resource_type/<xsl:value-of select="substring-after(., ':')"/></subfield>
				</datafield>
			</xsl:for-each>
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='contributor']/doc:element[@name='author']/doc:element/doc:field[@name='value']">
			<datafield ind2=" " ind1=" " tag="720">
				<subfield code="a"><xsl:value-of select="." /></subfield>
				<subfield code="e">author</subfield>
			</datafield>
			</xsl:for-each>
<!--			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='issued']/doc:element/doc:field[@name='value']">-->
<!--			<datafield ind2=" " ind1=" " tag="260">-->
<!--				<subfield code="c"><xsl:value-of select="." /></subfield>-->
<!--			</datafield>-->
<!--			</xsl:for-each>-->
			<xsl:if test="
				doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='issued']/doc:element/doc:field[@name='value']
			 	or doc:metadata/doc:element[@name='dc']/doc:element[@name='publisher']/doc:element[@name='place']/doc:element/doc:field[@name='value']
			 	or doc:metadata/doc:element[@name='dc']/doc:element[@name='publisher']/doc:element/doc:field[@name='value']
			">
				<datafield ind2=" " ind1=" " tag="260">
					<xsl:if test="doc:metadata/doc:element[@name='dc']/doc:element[@name='publisher']/doc:element[@name='place']/doc:element/doc:field[@name='value']">
						<subfield code="a"><xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='publisher']/doc:element[@name='place']/doc:element/doc:field[@name='value']/text()" /></subfield>
					</xsl:if>
					<xsl:if test="doc:metadata/doc:element[@name='dc']/doc:element[@name='publisher']/doc:element/doc:field[@name='value']">
						<subfield code="b"><xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='publisher']/doc:element/doc:field[@name='value']/text()" /></subfield>
					</xsl:if>
					<xsl:if test="doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='issued']/doc:element/doc:field[@name='value']">
						<subfield code="c"><xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='issued']/doc:element/doc:field[@name='value']/text()" /></subfield>
					</xsl:if>
				</datafield>
			</xsl:if>
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='description']/doc:element[@name='abstract']/doc:element/doc:field[@name='value']">
			<datafield ind2=" " ind1=" " tag="520">
				<subfield code="a"><xsl:value-of select="." /></subfield>
			</datafield>
			</xsl:for-each>
<!--			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element/doc:element/doc:field[@name='value']">-->
<!--			<datafield ind1="8" ind2=" " tag="024">-->
<!--				<subfield code="a"><xsl:value-of select="." /></subfield>-->
<!--			</datafield>-->
<!--			</xsl:for-each>-->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='uri']/doc:element/doc:field[@name='value']">
				<datafield ind2="0" ind1="7" tag="024">
					<subfield code="a"><xsl:value-of select="." /></subfield>
					<subfield code="2">url</subfield>
				</datafield>
			</xsl:for-each>
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='doi']/doc:element/doc:field[@name='value']">
				<datafield ind2="0" ind1="7" tag="024">
					<subfield code="a"><xsl:value-of select="." /></subfield>
					<subfield code="2">doi</subfield>
				</datafield>
			</xsl:for-each>
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='isbn']/doc:element/doc:field[@name='value']">
				<datafield ind2=" " ind1=" " tag="020">
					<subfield code="a"><xsl:value-of select="." /></subfield>
				</datafield>
			</xsl:for-each>
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='issn']/doc:element/doc:field[@name='value']">
				<datafield ind2=" " ind1=" " tag="022">
					<subfield code="a"><xsl:value-of select="." /></subfield>
				</datafield>
			</xsl:for-each>
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='subject']/doc:element/doc:field[@name='value']">
			<datafield tag="653" ind2=" " ind1=" " >
				<subfield code="a"><xsl:value-of select="." /></subfield>
			</datafield>
			</xsl:for-each>
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='title']/doc:element/doc:field[@name='value']">
			<datafield ind2="0" ind1="0" tag="245">
				<subfield code="a"><xsl:value-of select="." /></subfield>
			</datafield>
			</xsl:for-each>
			<xsl:if test="
			 	doc:metadata/doc:element[@name='oaire']/doc:element[@name='citation']/doc:element[@name='volume']/doc:element/doc:field[@name='value']
			 	or doc:metadata/doc:element[@name='oaire']/doc:element[@name='citation']/doc:element[@name='issue']/doc:element/doc:field[@name='value']
			 	or doc:metadata/doc:element[@name='oaire']/doc:element[@name='citation']/doc:element[@name='startPage']/doc:element/doc:field[@name='value']
			 	or doc:metadata/doc:element[@name='dc']/doc:element[@name='relation']/doc:element[@name='journal']/doc:element/doc:field[@name='value']
			">
				<datafield ind2=" " ind1=" " tag="773">
					<xsl:if test="doc:metadata/doc:element[@name='oaire']/doc:element[@name='citation']/doc:element[@name='volume']/doc:element/doc:field[@name='value']">
						<subfield code="j"><xsl:value-of select="doc:metadata/doc:element[@name='oaire']/doc:element[@name='citation']/doc:element[@name='volume']/doc:element/doc:field[@name='value']/text()" /></subfield>
					</xsl:if>
					<xsl:if test="doc:metadata/doc:element[@name='oaire']/doc:element[@name='citation']/doc:element[@name='issue']/doc:element/doc:field[@name='value']">
						<subfield code="k"><xsl:value-of select="doc:metadata/doc:element[@name='oaire']/doc:element[@name='citation']/doc:element[@name='issue']/doc:element/doc:field[@name='value']/text()" /></subfield>
					</xsl:if>
					<xsl:if test="doc:metadata/doc:element[@name='oaire']/doc:element[@name='citation']/doc:element[@name='startPage']/doc:element/doc:field[@name='value']">
						<subfield code="q"><xsl:value-of select="doc:metadata/doc:element[@name='oaire']/doc:element[@name='citation']/doc:element[@name='startPage']/doc:element/doc:field[@name='value']/text()" /> - <xsl:value-of select="doc:metadata/doc:element[@name='oaire']/doc:element[@name='citation']/doc:element[@name='endPage']/doc:element/doc:field[@name='value']/text()" /></subfield>
					</xsl:if>
					<xsl:if test="doc:metadata/doc:element[@name='dc']/doc:element[@name='relation']/doc:element[@name='journal']/doc:element/doc:field[@name='value']">
						<subfield code="t"><xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='relation']/doc:element[@name='journal']/doc:element/doc:field[@name='value']/text()" /> </subfield>
					</xsl:if>
				</datafield>
			</xsl:if>
			<datafield ind2=" " ind1=" " tag="918">
				<xsl:for-each select="doc:metadata/doc:element[@name='epfl']/doc:element[@name='thesis']/doc:element[@name='faculty']/doc:element/doc:field[@name='value']">
					<subfield code="a"><xsl:value-of select="." /></subfield>
				</xsl:for-each>
				<xsl:for-each select="doc:metadata/doc:element[@name='epfl']/doc:element[@name='thesis']/doc:element[@name='section']/doc:element/doc:field[@name='value']">
					<subfield code="b"><xsl:value-of select="." /></subfield>
				</xsl:for-each>
				<xsl:for-each select="doc:metadata/doc:element[@name='epfl']/doc:element[@name='thesis']/doc:element[@name='institute']/doc:element/doc:field[@name='value']">
					<subfield code="c"><xsl:value-of select="." /></subfield>
				</xsl:for-each>
				<xsl:for-each select="doc:metadata/doc:element[@name='epfl']/doc:element[@name='thesis']/doc:element[@name='doctoralSchool']/doc:element/doc:field[@name='value']">
					<subfield code="d"><xsl:value-of select="." /></subfield>
				</xsl:for-each>
			</datafield>

			<!-- These variables are needed because oairecerif.author.affiliation value would be evaluated incorrectly inside the following 'for-each'-->
			<xsl:variable name="hasAuthorAffiliation">
				<xsl:if test="doc:metadata/doc:element[@name='oairecerif']/doc:element[@name='author']/doc:element[@name='affiliation']/doc:element/doc:field[@name='value']">
					<xsl:text>true</xsl:text>
				</xsl:if>
			</xsl:variable>
			<xsl:variable name="authorAffiliation">
				<xsl:if test="$hasAuthorAffiliation='true'">
					<xsl:value-of select="doc:metadata/doc:element[@name='oairecerif']/doc:element[@name='author']/doc:element[@name='affiliation']/doc:element/doc:field[@name='value']/text()" />
				</xsl:if>
			</xsl:variable>

			<xsl:for-each select="doc:metadata/doc:element[@name='bundles']/doc:element[@name='bundle']">
				<xsl:if test="doc:field[@name='name']/text() = 'ORIGINAL'">
					<xsl:for-each select="doc:element[@name='bitstreams']/doc:element">
						<datafield ind2=" " ind1="4" tag="856">
							<xsl:if test="doc:field[@name='format']">
								<subfield code="z">
									<xsl:value-of select="doc:field[@name='format']/text()" /></subfield>
							</xsl:if>
							<xsl:if test="doc:field[@name='name']">
								<subfield code="f"><xsl:value-of select="doc:field[@name='name']/text()" /></subfield>
							</xsl:if>
							<xsl:if test="doc:field[@name='rights']">
								<subfield code="e"><xsl:value-of select="doc:field[@name='rights']/text()" /></subfield>
							</xsl:if>
							<xsl:if test="doc:field[@name='url']">
								<subfield code="u"><xsl:value-of select="doc:field[@name='url']/text()" /></subfield>
							</xsl:if>
						</datafield>
					</xsl:for-each>
					<xsl:if test="doc:element[@name='bitstreams']/doc:field[@name='elements'] or $hasAuthorAffiliation='true'">
						<datafield ind2=" " ind1=" " tag="919">
							<xsl:if test="doc:element[@name='bitstreams']/doc:field[@name='elements']">
								<subfield code="o"><xsl:value-of select="doc:element[@name='bitstreams']/doc:field[@name='elements']/text()" /></subfield>
							</xsl:if>
							<xsl:if test="$hasAuthorAffiliation='true'">
								<subfield code="a"><xsl:value-of select="$authorAffiliation" /></subfield>
							</xsl:if>
						</datafield>
					</xsl:if>
				</xsl:if>
			</xsl:for-each>
		</record>
	</xsl:template>
</xsl:stylesheet>
