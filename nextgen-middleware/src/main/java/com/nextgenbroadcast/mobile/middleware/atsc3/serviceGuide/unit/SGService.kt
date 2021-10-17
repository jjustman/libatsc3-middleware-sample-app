package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit

/*
<xs:element name="Service" type="ServiceType"/>
	<xs:complexType name="LanguageString">
		<xs:simpleContent>
			<xs:extension base="xs:string">
				<xs:attribute ref="xml:lang" use="optional"/>
			</xs:extension>
		</xs:simpleContent>
	</xs:complexType>
	<xs:complexType name="ParentalRatingType">
		<xs:simpleContent>
			<xs:extension base="xs:string">
				<xs:attribute name="ratingSystem" type="xs:unsignedByte" use="optional"/>
				<xs:attribute name="ratingValueName" type="xs:string" use="optional"/>
			</xs:extension>
		</xs:simpleContent>
	</xs:complexType>
	<!-- Service fragment -->
	<xs:complexType name="ServiceType">
		<xs:sequence>
			<xs:element name="ProtectionKeyID" type="ProtectionKeyIDType" minOccurs="0" maxOccurs="unbounded"/>
			<xs:element name="ServiceType" type="ServiceTypeRangeType" minOccurs="0" maxOccurs="unbounded"/>
			<!-- Start of program guide information -->
			<xs:element name="Name" type="LanguageString" maxOccurs="unbounded"/>
			<xs:element name="Description" type="LanguageString" minOccurs="0" maxOccurs="unbounded"/>
			<xs:element name="AudioLanguage" type="AudioOrTextLanguageType" minOccurs="0" maxOccurs="unbounded"/>
			<xs:element name="TextLanguage" type="AudioOrTextLanguageType" minOccurs="0" maxOccurs="unbounded"/>
			<xs:element name="ParentalRating" type="ParentalRatingType" minOccurs="0" maxOccurs="unbounded"/>
			<xs:element name="TargetUserProfile" type="TargetUserProfileType" minOccurs="0" maxOccurs="unbounded"/>
			<xs:element name="Genre" type="GenreType" minOccurs="0" maxOccurs="unbounded"/>
			<xs:element name="Extension" type="ExtensionType" minOccurs="0" maxOccurs="unbounded"/>
			<!-- End of program guide information -->
			<xs:element name="PreviewDataReference" type="PreviewDataReferenceType" minOccurs="0" maxOccurs="unbounded"/>
			<xs:element name="BroadcastArea" type="BroadcastAreaType" minOccurs="0"/>
			<xs:element name="Popularity" type="PopularityType" minOccurs="0"/>
			<xs:element name="TermsOfUse" type="TermsOfUseType" minOccurs="0" maxOccurs="unbounded"/>
			<xs:element name="PrivateExt" type="PrivateExtType" minOccurs="0"/>
		</xs:sequence>
		<xs:attribute name="id" type="xs:anyURI" use="required"/>
		<xs:attribute name="version" type="xs:unsignedInt" use="required"/>
		<xs:attribute name="validFrom" type="xs:unsignedInt" use="optional"/>
		<xs:attribute name="validTo" type="xs:unsignedInt" use="optional"/>
		<xs:attribute name="globalServiceID" type="xs:anyURI" use="optional"/>
		<xs:attribute name="weight" type="xs:unsignedShort" use="optional" default="65535"/>
		<xs:attribute name="hidden" type="xs:boolean" use="optional" default="false"/>
		<xs:attribute name="baseCID" type="xs:string" use="optional"/>
		<xs:attribute name="emergency" type="xs:boolean" use="optional" default="false"/>
		<xs:attribute name="UDBAllowed" type="xs:boolean" use="optional"/>
		<xs:attribute name="amAllowed" type="xs:boolean" use="optional" default="true"/>
	</xs:complexType>
	<!-- Genre -->
	<xs:simpleType name="GenreTypeType">
		<xs:restriction base="xs:string">
			<xs:enumeration value="main"/>
			<xs:enumeration value="secondary"/>
			<xs:enumeration value="other"/>
		</xs:restriction>
	</xs:simpleType>
	<xs:complexType name="GenreType">
		<xs:simpleContent>
			<xs:extension base="xs:string">
				<xs:attribute ref="xml:lang" use="optional"/>
				<xs:attribute name="type" type="GenreTypeType" use="optional"/>
				<xs:attribute name="href" type="xs:anyURI" use="optional"/>
			</xs:extension>
		</xs:simpleContent>
	</xs:complexType>
	<!--
		Type of ProtectionKeyID:
		0 - 5-byte long concatenation of Key Domain ID and Key group part of the SEK/PEK ID, as defined in the Smartcard Profile [BCAST10-ServContProt].
		1-127 Reserved for future use
		128-255 Reserved for proprietary use
	-->
	<xs:simpleType name="ReservedProprietaryExtByteRangeType">
		<xs:restriction base="xs:unsignedByte">
			<xs:minInclusive value="128"/>
			<xs:maxInclusive value="255"/>
		</xs:restriction>
	</xs:simpleType>
	<xs:simpleType name="ProtectionKeyIDRangeType">
		<xs:union memberTypes="ProtectionKeyIDLRType ReservedProprietaryExtByteRangeType"/>
	</xs:simpleType>
	<xs:simpleType name="ProtectionKeyIDLRType">
		<xs:restriction base="xs:unsignedByte">
			<xs:minInclusive value="0"/>
			<xs:maxInclusive value="0"/>
		</xs:restriction>
	</xs:simpleType>
	<xs:complexType name="ProtectionKeyIDType">
		<xs:simpleContent>
			<xs:extension base="xs:base64Binary">
				<xs:attribute name="type" type="ProtectionKeyIDRangeType" use="required"/>
			</xs:extension>
		</xs:simpleContent>
	</xs:complexType>
	<!-- Variant of ProtectionKeyID specifically for PPV -->
	<xs:complexType name="ProtectionKeyIDMinMaxType">
		<xs:simpleContent>
			<xs:extension base="xs:base64Binary">
				<xs:attribute name="type" type="ProtectionKeyIDRangeType" use="required"/>
				<xs:attribute name="min" type="xs:nonNegativeInteger" use="optional"/>
				<xs:attribute name="max" type="xs:nonNegativeInteger" use="optional"/>
			</xs:extension>
		</xs:simpleContent>
	</xs:complexType>
	<!--
		Allowed values are:
		0 - unspecified
		1 - Basic TV
		2 - Basic Radio
		3 - Rights Issuer Service
		4 - Cachecast
		5 - File download services
		6 - Software management services
		7 – Notification
		8 – Service Guide
		9 - Terminal Provisioning services
		10 - Auxiliary Data
		11 – Streaming on demand
		12 – File download on demand
		13 - Smartcard Provisioning services
		14 - 127 reserved for future use
		128- 223 reserved for other OMA enablers
		224- 255 reserved for proprietary use
	-->
	<xs:simpleType name="ServiceTypeRangeType">
		<xs:union memberTypes="ServiceTypeLRType ServiceTypeOtherEnablersRangeType ServiceTypeProprietaryRangeType"/>
	</xs:simpleType>
	<xs:simpleType name="ServiceTypeLRType">
		<xs:restriction base="xs:unsignedByte">
			<xs:minInclusive value="0"/>
			<xs:maxInclusive value="13"/>
		</xs:restriction>
	</xs:simpleType>
	<xs:simpleType name="ServiceTypeOtherEnablersRangeType">
		<xs:restriction base="xs:unsignedByte">
			<xs:minInclusive value="128"/>
			<xs:maxInclusive value="223"/>
		</xs:restriction>
	</xs:simpleType>
	<xs:simpleType name="ServiceTypeProprietaryRangeType">
		<xs:restriction base="xs:unsignedByte">
			<xs:minInclusive value="224"/>
			<xs:maxInclusive value="255"/>
		</xs:restriction>
	</xs:simpleType>
	<xs:complexType name="TargetUserProfileType">
		<xs:attribute name="attributeName" type="xs:string" use="required"/>
		<xs:attribute name="attributeValue" type="xs:string" use="required"/>
	</xs:complexType>
	<xs:complexType name="ExtensionType">
		<xs:sequence>
			<xs:element name="Description" type="LanguageString" minOccurs="0" maxOccurs="unbounded"/>
		</xs:sequence>
		<xs:attribute name="url" type="xs:anyURI" use="required"/>
	</xs:complexType>
	<!--
		Possible values:
		0. unspecified
		1.	Service-by-Service switching
		2.	Service Guide Browsing
		3.	Service preview
		4.	Barker
		5. Alternative to blackout
		6-127. reserved for future use
		128-255. reserved for proprietary use
	-->
	<xs:simpleType name="PreviewDataUsageRangeType">
		<xs:union memberTypes="PreviewDataUsageLRType ReservedProprietaryExtByteRangeType"/>
	</xs:simpleType>
	<xs:simpleType name="PreviewDataUsageLRType">
		<xs:restriction base="xs:unsignedByte">
			<xs:minInclusive value="0"/>
			<xs:maxInclusive value="5"/>
		</xs:restriction>
	</xs:simpleType>
	<xs:complexType name="PreviewDataReferenceType">
		<xs:attribute name="idRef" type="xs:anyURI" use="required"/>
		<xs:attribute name="usage" type="PreviewDataUsageRangeType" use="required"/>
	</xs:complexType>
	<xs:complexType name="BroadcastAreaType">
		<xs:sequence>
			<xs:element name="TargetArea" type="TargetAreaType" minOccurs="0" maxOccurs="unbounded"/>
			<xs:element name="lev_conf" type="LevConfType" minOccurs="0">
				<xs:annotation>
					<xs:documentation> See [OMA MLP]</xs:documentation>
				</xs:annotation>
			</xs:element>
			<xs:element name="LocationFilter" type="LocationFilterType" minOccurs="0"/>
		</xs:sequence>
		<xs:attribute name="polarity" type="xs:boolean" use="optional" default="true"/>
		<xs:attribute name="filteringTime" type="FilteringTimeType" use="optional"/>
	</xs:complexType>
	<!--
		Type of CellTargetArea.
		Allowed values are:
		 0 –  Unspecified
		 1 - 3GPP Cell Global Identifier as defined in [3GPP TS 23.003]
		 2 – 3GPP Routing Area Identifier (RAI) as defined in [3GPP TS 23.003]
		 3 – 3GPP Location Area Identifier (LAI) as defined in [3GPP TS 23.003]
		 4 – 3GPP Service Area Identifier (SAI) as defined in [3GPP TS 23.003]
		 5 – 3GPP MBMS Service Area Identity (MBMS SAI) as defined in [3GPP TS 23.003]
		 6 – 3GPP2 Subnet ID as defined in [3GPP2 X.S0022-A]
		 7 – 3GPP2 SID as defined in [3GPP2 C.S0005-E]
		 8 – 3GPP2 SID+NID as defined in [3GPP2 C.S0005-E]
		 9 – 3GPP2 SID+NID+PZID as defined in [3GPP2 C.S0005-E]
		10 – 3GPP2 SID+PZID as defined in [3GPP2 C.S0005-E]
		11 – DVB-H Cell ID  (specified in section 6.3.4.1 of [BCAST11-DVBH-IPDC-Adaptation] )
		12 – DVB-SH Cell ID (specified in section 6.3.4.1.1 of [BCAST11-DVBSH-IPDC-Adaptation])
		13 –  WiMAX Base Station Identifier (BSID) as defined in [IEEE 802.16-2004] and [IEEE 802.16e-2005]
		14 – WiMAX Operator ID (NAP ID) as defined in in [IEEE 802.16-2004] and [IEEE 802.16e-2005]
		15 – Forward Link Only Cell ID (specified in section 6.3 of [BCAST11-FLO-Adaptation])
		16 – DVB-SH DVB service ID (specified in section 6.3.4.1.2 of [BCAST11-DVBSH-IPDC-Adaptation]).
		17 - 127  reserved for future use
		128 -255 reserved for proprietary use
	-->
	<xs:simpleType name="CellTargetAreaRangeType">
		<xs:union memberTypes="CellTargetAreaLRType ReservedProprietaryExtByteRangeType"/>
	</xs:simpleType>
	<xs:simpleType name="CellTargetAreaLRType">
		<xs:restriction base="xs:unsignedByte">
			<xs:minInclusive value="0"/>
			<xs:maxInclusive value="16"/>
		</xs:restriction>
	</xs:simpleType>
	<xs:complexType name="CellTargetAreaType">
		<xs:sequence>
			<xs:element name="CellArea" maxOccurs="unbounded">
				<xs:complexType>
					<xs:sequence>
						<xs:element name="PP2CellID" type="xs:positiveInteger" minOccurs="0" maxOccurs="unbounded"/>
					</xs:sequence>
					<xs:attribute name="value" type="xs:string" use="required"/>
				</xs:complexType>
			</xs:element>
		</xs:sequence>
		<xs:attribute name="type" type="CellTargetAreaRangeType" use="required"/>
	</xs:complexType>
	<!--
		Type of filteringTime.
		Allowed values are:
		 0 – download time specified by DistributionWindow in corresponding Schedule fragment.
		 1 – rendering time specified by PresentationWindow in corresponding Schedule fragment.
		 2 – location-based filter applied as soon as this fragment received by terminal.
		 3-255 – reserved for future use.
	-->
	<xs:simpleType name="FilteringTimeType">
		<xs:restriction base="xs:unsignedByte">
			<xs:minInclusive value="0"/>
			<xs:maxInclusive value="2"/>
		</xs:restriction>
	</xs:simpleType>
	<!-- Terms of Use -->
	<!--
		Allowed values for type of Terms of Use
		0 - Display before purchasing or subscribing.
		1 - Display before playout.
		2 - 127  reserved for future use
		128 -255 reserved for proprietary use
	-->
	<xs:simpleType name="TermsOfUseRangeType">
		<xs:union memberTypes="TermsOfUseLRType ReservedProprietaryExtByteRangeType"/>
	</xs:simpleType>
	<xs:simpleType name="TermsOfUseLRType">
		<xs:restriction base="xs:unsignedByte">
			<xs:minInclusive value="0"/>
			<xs:maxInclusive value="1"/>
		</xs:restriction>
	</xs:simpleType>
	<xs:complexType name="TermsOfUseType">
		<xs:sequence>
			<xs:element name="Country" minOccurs="0" maxOccurs="unbounded" type="MCCType"/>
			<xs:element name="Language" type="xs:string"/>
			<xs:choice>
				<xs:element name="PreviewDataIDRef" type="xs:anyURI" />
				<xs:element name="TermsOfUseText" type="xs:string"/>
			</xs:choice>
		</xs:sequence>
		<xs:attribute name="type" type="TermsOfUseRangeType" use="required"/>
		<xs:attribute name="id" type="xs:anyURI" use="required"/>
		<xs:attribute name="userConsentRequired" type="xs:boolean" use="required"/>
	</xs:complexType>
	<!-- Popularity -->
	<xs:complexType name="PopularityType">
		<xs:attribute name="rating" type="xs:decimal" use="optional"/>
		<xs:attribute name="noOfViews" type="xs:unsignedInt" use="optional"/>
		<xs:attribute name="noOfDiscussions" type="xs:unsignedInt" use="optional"/>
		<xs:attribute name="samplingDate" type="xs:dateTime" use="optional"/>
	</xs:complexType>
	<!-- Target Area -->
	<xs:complexType name="TargetAreaType">
		<xs:choice>
			<xs:element name="shape">
				<xs:annotation>
					<xs:documentation> See [OMA MLP]</xs:documentation>
				</xs:annotation>
				<xs:complexType>
					<xs:sequence>
						<xs:any minOccurs="0" maxOccurs="unbounded" namespace="##other" processContents="skip"/>
					</xs:sequence>
				</xs:complexType>
			</xs:element>
			<xs:element name="cc" type="xs:unsignedShort">
				<xs:annotation>
					<xs:documentation> See [OMA MLP]</xs:documentation>
				</xs:annotation>
			</xs:element>
			<xs:element name="mcc" type="MCCType"/>
			<xs:element name="name_area" type="LanguageString" maxOccurs="unbounded">
				<xs:annotation>
					<xs:documentation> See [OMA MLP]. The instances of this element only differ in language.</xs:documentation>
				</xs:annotation>
			</xs:element>
			<xs:element name="ZipCode" type="xs:string"/>
			<xs:element name="CellTargetArea" type="CellTargetAreaType"/>
		</xs:choice>
	</xs:complexType>
	<!-- Location Filter -->
	<xs:complexType name="LocationFilterType">
		<xs:sequence>
			<xs:element name="LocationFilter1" type="LocationFilterType" minOccurs="0"/>
			<xs:element name="LogicalOperation" type="LogicalOperationType" minOccurs="0"/>
			<xs:choice>
				<xs:element name="LocationRequirement2" type="LocationRequirementType" minOccurs="0"/>
				<xs:element name="LocationFilter2" type="LocationFilterType" minOccurs="0"/>
			</xs:choice>
		</xs:sequence>
	</xs:complexType>
	<!-- Logical Operation -->
	<!--
		Allowed values for type of Logical Operation
		0 – unspecified
		1 – Binary operator "AND"
		2 – Binary operator "OR"
		3 – Unary operator "NOT"
		4-127 – reserved for future use
		128-255 – reserved for proprietary use
	-->
	<xs:simpleType name="LogicalOperationType">
		<xs:union memberTypes="LogicalOperationLRType ReservedProprietaryExtByteRangeType"/>
	</xs:simpleType>
	<xs:simpleType name="LogicalOperationLRType">
		<xs:restriction base="xs:unsignedByte">
			<xs:minInclusive value="0"/>
			<xs:maxInclusive value="3"/>
		</xs:restriction>
	</xs:simpleType>
	<!-- Location Requirement -->
	<xs:complexType name="LocationRequirementType">
		<xs:sequence>
			<xs:element name="TargetArea" type="TargetAreaType" maxOccurs="unbounded"/>
			<xs:element name="StartTime" type="xs:unsignedInt" minOccurs="0"/>
			<xs:element name="EndTime" type="xs:unsignedInt" minOccurs="0"/>
			<xs:element name="Duration" type="xs:unsignedInt" minOccurs="0"/>
			<xs:element name="Lev_Conf_Present" type="xs:decimal" minOccurs="0"/>
			<xs:element name="Lev_Conf_Absent" type="xs:decimal" minOccurs="0"/>
		</xs:sequence>
	</xs:complexType>
 */

internal class SGService(
        // required
        var serviceId: Int = 0,
        override var version: Long = 0,
        // non required
        var globalServiceId: String? = null,
        var majorChannelNo: Int = 0,
        var minorChannelNo: Int = 0,
        var shortServiceName: String? = null,
        // links
        var scheduleMap: MutableMap<String, SGSchedule>? = null,
        var bsid: Int = 0
) : SGUnit() {
    fun addSchedule(schedule: SGSchedule) {
        val scheduleId = schedule.id ?: return
        (scheduleMap ?: mutableMapOf<String, SGSchedule>().also { scheduleMap = it })[scheduleId] = schedule
    }
}