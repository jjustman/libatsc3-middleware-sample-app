package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit

import java.util.*

/*
<xs:element name="Content" type="ContentType"/>
<xs:complexType name="ContentType">
    <xs:sequence>
        <xs:element name="ServiceReference" minOccurs="0" maxOccurs="unbounded">
            <xs:complexType>
                <xs:attribute name="idRef" type="xs:anyURI" use="required"/>
                <xs:attribute name="weight" type="xs:unsignedShort" use="optional" default="65535"/>
            </xs:complexType>
        </xs:element>
        <xs:element name="ProtectionKeyID" type="ProtectionKeyIDMinMaxType" minOccurs="0" maxOccurs="unbounded"/>
        <!-- Start of program guide information -->
        <xs:element name="Name" type="LanguageString" maxOccurs="unbounded"/>
        <xs:element name="Description" type="LanguageString" minOccurs="0" maxOccurs="unbounded"/>
        <xs:element name="StartTime" type="xs:dateTime" minOccurs="0"/>
        <xs:element name="EndTime" type="xs:dateTime" minOccurs="0"/>
        <xs:element name="AudioLanguage" type="AudioOrTextLanguageType" minOccurs="0" maxOccurs="unbounded"/>
        <xs:element name="TextLanguage" type="AudioOrTextLanguageType" minOccurs="0" maxOccurs="unbounded"/>
        <xs:element name="Length" type="xs:duration" minOccurs="0"/>
        <xs:element name="ParentalRating" type="ParentalRatingType" minOccurs="0" maxOccurs="unbounded"/>
        <xs:element name="TargetUserProfile" type="TargetUserProfileType" minOccurs="0" maxOccurs="unbounded"/>
        <xs:element name="Genre" type="GenreType" minOccurs="0" maxOccurs="unbounded"/>
        <xs:element name="TermsOfUse" type="TermsOfUseType" minOccurs="0" maxOccurs="unbounded"/>
        <xs:element name="Extension" type="ExtensionType" minOccurs="0" maxOccurs="unbounded"/>
        <!-- End of program guide information -->
        <xs:element name="PreviewDataReference" type="PreviewDataReferenceType" minOccurs="0" maxOccurs="unbounded"/>
        <xs:element name="BroadcastArea" type="BroadcastAreaType" minOccurs="0"/>
        <xs:element name="Popularity" type="PopularityType" minOccurs="0"/>
        <xs:element name="Freshness" type="FreshnessType" minOccurs="0"/>
        <xs:element name="PrivateExt" type="PrivateExtType" minOccurs="0"/>
    </xs:sequence>
    <xs:attribute name="id" type="xs:anyURI" use="required"/>
    <xs:attribute name="version" type="xs:unsignedInt" use="required"/>
    <xs:attribute name="validFrom" type="xs:unsignedInt" use="optional"/>
    <xs:attribute name="validTo" type="xs:unsignedInt" use="optional"/>
    <xs:attribute name="globalContentID" type="xs:anyURI" use="optional"/>
    <xs:attribute name="emergency" type="xs:boolean" use="optional" default="false"/>
    <xs:attribute name="baseCID" type="xs:string" use="optional"/>
    <xs:attribute name="UDBAllowed" type="xs:boolean" use="optional"/>
    <xs:attribute name="amAllowed" type="xs:boolean" use="optional" default="true"/>
</xs:complexType>
<xs:complexType name="AudioOrTextLanguageType">
    <xs:simpleContent>
        <xs:extension base="LanguageString">
            <xs:attribute name="languageSDPTag" type="xs:string" use="required"/>
        </xs:extension>
    </xs:simpleContent>
</xs:complexType>
<!-- Freshness -->
<xs:complexType name="FreshnessType">
    <xs:attribute name="releaseDate" type="xs:dateTime" use="optional"/>
    <xs:attribute name="broadcastDate" type="xs:dateTime" use="optional"/>
</xs:complexType>
 */

internal class SGContent(
        // required
        var id: String? = null,
        override var version: Long = 0,
        // non required
        var nameMap: MutableMap<String, String>? = null,
        var descriptionMap: MutableMap<Locale, String>? = null,
        var icon: String? = null,
        // links
        var serviceIdList: MutableList<Int>? = null
) : SGUnit() {
    fun getName(local: Locale): String? {
        return nameMap?.let { map ->
            map[local.language] ?: map.values.firstOrNull()
        }
    }

    fun getDescription(local: Locale): String? {
        return descriptionMap?.let { map ->
            map[local] ?: map.values.firstOrNull()
        }
    }

    fun addName(name: String, lang: String) {
        (nameMap ?: mutableMapOf<String, String>().also { nameMap = it })[lang] = name
    }

    fun addDescription(description: String, lang: String) {
        (descriptionMap ?: mutableMapOf<Locale, String>().also { descriptionMap = it })[Locale(lang)] = description
    }

    fun addServiceId(serviceId: Int) {
        (serviceIdList ?: mutableListOf<Int>().also { serviceIdList = it }).add(serviceId)
    }
}