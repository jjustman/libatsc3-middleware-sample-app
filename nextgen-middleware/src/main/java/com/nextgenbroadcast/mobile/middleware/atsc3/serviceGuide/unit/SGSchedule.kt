package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit

/*
<xs:element name="Schedule" type="ScheduleType"/>
	<xs:complexType name="ScheduleType">
		<xs:sequence>
			<xs:element name="ServiceReference">
				<xs:complexType>
					<xs:attribute name="idRef" type="xs:anyURI" use="required"/>
				</xs:complexType>
			</xs:element>
			<xs:element name="InteractivityDataReference" minOccurs="0" maxOccurs="unbounded">
				<xs:complexType>
					<xs:sequence>
						<xs:element name="AutoStart" type="xs:unsignedInt" minOccurs="0" maxOccurs="unbounded"/>
						<xs:element name="DistributionWindow" type="DistributionWindowType" minOccurs="0" maxOccurs="unbounded"/>
					</xs:sequence>
					<xs:attribute name="idRef" type="xs:anyURI" use="required"/>
				</xs:complexType>
			</xs:element>
			<xs:element name="ContentReference" minOccurs="0" maxOccurs="unbounded">
				<xs:complexType>
					<xs:sequence>
						<xs:element name="AutoStart" type="xs:unsignedInt" minOccurs="0" maxOccurs="unbounded"/>
						<xs:element name="DistributionWindow" type="DistributionWindowType" minOccurs="0" maxOccurs="unbounded"/>
						<xs:element name="PresentationWindow" type="PresentationWindowType" minOccurs="0" maxOccurs="unbounded"/>
					</xs:sequence>
					<xs:attribute name="idRef" type="xs:anyURI" use="required"/>
					<xs:attribute name="contentLocation" type="xs:anyURI" use="optional"/>
					<xs:attribute name="repeatPlayback" type="xs:boolean" use="optional" default="false"/>
				</xs:complexType>
			</xs:element>
			<xs:element name="PreviewDataReference" type="PreviewDataReferenceType" minOccurs="0" maxOccurs="unbounded"/>
			<xs:element name="TermsOfUse" type="TermsOfUseType" minOccurs="0" maxOccurs="unbounded"/>
			<xs:element name="PrivateExt" type="PrivateExtType" minOccurs="0"/>
		</xs:sequence>
		<xs:attribute name="id" type="xs:anyURI" use="required"/>
		<xs:attribute name="version" type="xs:unsignedInt" use="required"/>
		<xs:attribute name="defaultSchedule" type="xs:boolean" use="optional" fixed="true"/>
		<xs:attribute name="onDemand" type="xs:boolean" use="optional" default="false"/>
		<xs:attribute name="validFrom" type="xs:unsignedInt" use="optional"/>
		<xs:attribute name="validTo" type="xs:unsignedInt" use="optional"/>
		<xs:attribute name="UDBAllowed" type="xs:boolean" use="optional"/>
	</xs:complexType>
	<xs:complexType name="DistributionWindowType">
		<xs:attribute name="startTime" type="xs:unsignedInt" use="optional"/>
		<xs:attribute name="endTime" type="xs:unsignedInt" use="optional"/>
		<xs:attribute name="duration" type="xs:unsignedInt" use="optional"/>
		<xs:attribute name="id" type="xs:unsignedInt" use="optional"/>
	</xs:complexType>
	<xs:complexType name="PresentationWindowType">
		<xs:attribute name="startTime" type="xs:unsignedInt" use="optional"/>
		<xs:attribute name="endTime" type="xs:unsignedInt" use="optional"/>
		<xs:attribute name="duration" type="xs:unsignedInt" use="optional"/>
		<xs:attribute name="id" type="xs:unsignedInt" use="optional"/>
	</xs:complexType>
*/

internal class SGSchedule (
        // required
        var id: String? = null,
        var serviceId: Int = -1,
        override var version: Long = 0,
        // links
        var contentMap: MutableMap<String, SGScheduleContent>? = null
) : SGUnit() {
    fun addContent(scheduleContent: SGScheduleContent) {
        val contentId = scheduleContent.contentId ?: return
        (contentMap ?: mutableMapOf<String, SGScheduleContent>().also { contentMap = it })[contentId] = scheduleContent
    }
}