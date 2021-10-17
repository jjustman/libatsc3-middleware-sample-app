package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db

import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.IServiceGuideStore
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities.*
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit.*
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit.SGContent
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit.SGSchedule
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit.SGScheduleContent
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit.SGService

internal class RoomServiceGuideStore(
        private val db: SGDataBase
) : IServiceGuideStore {

    private var notifyUpdated: (() -> Unit)? = null

    fun subscribe(notifyUpdated: () -> Unit) {
        this.notifyUpdated = notifyUpdated
    }

    fun unsubscribe() {
        notifyUpdated = null
    }

    override fun clearAll() {
        db.serviceDAO().deleteAll()
        db.scheduleDAO().deleteAll()
        db.scheduleContentDAO().deleteAll()
        db.presentationDAO().deleteAll()
        db.contentDAO().deleteAll()
        db.contentNameDAO().deleteAll()
        db.contentDescriptionDAO().deleteAll()
        db.contentServiceIdDAO().deleteAll()

        notifyUpdated?.invoke()
    }

    override fun storeService(serviceMap: Map<Int, SGService>) {
        val services = serviceMap.values

        storeServiceList(services)

        services.forEach { service ->
            val schedules = service.scheduleMap?.values
            if (schedules != null) {
                storeScheduleList(service.serviceId, schedules)

                schedules.forEach { schedule ->
                    val contents = schedule.contentMap?.values
                    if (contents != null) {
                        schedule.id?.let { id ->
                            saveScheduleContentList(id, contents)
                        }

                        contents.forEach {  content ->
                            val presentations = content.presentationList
                            if (presentations != null) {
                                content.contentId?.let { id ->
                                    storePresentationList(id, presentations)
                                }
                            }
                        }
                    }
                }
            }
        }

        notifyUpdated?.invoke()
    }

    private fun storeServiceList(services: Collection<SGService>) {
        db.serviceDAO().insert(
                services.map { service ->
                    SGServiceEntity(
                            service.serviceId,
                            service.globalServiceId,
                            service.majorChannelNo,
                            service.minorChannelNo,
                            service.shortServiceName,
                            service.version,
                            service.bsid
                    )
                }
        )
    }

    private fun storeScheduleList(serviceId: Int, schedules: Collection<SGSchedule>) {
        db.scheduleDAO().insert(
                schedules.mapNotNull { schedule ->
                    schedule.id?.let { id ->
                        SGScheduleEntity(id, serviceId, schedule.version)
                    }
                }
        )
    }

    private fun saveScheduleContentList(scheduleId: String, contents: Collection<SGScheduleContent>) {
        db.scheduleContentDAO().insert(
                contents.mapNotNull { content ->
                    content.contentId?.let { id ->
                        SGScheduleContentEntity(scheduleId, id)
                    }
                }
        )
    }

    private fun storePresentationList(contentId: String, presentations: Collection<SGPresentation>) {
        db.presentationDAO().insert(
                presentations.map { presentation ->
                    SGPresentationEntity(
                            contentId,
                            presentation.startTime,
                            presentation.endTime,
                            presentation.duration
                    )
                }
        )
    }

    override fun storeContent(contentMap: Map<String, SGContent>) {
        val contents = contentMap.values

        storeContentList(contents)

        val contentNameDAO = db.contentNameDAO()
        val contentDescriptionDAO = db.contentDescriptionDAO()
        val contentServiceIdDAO = db.contentServiceIdDAO()

        contents.forEach { content ->

            content.id?.let { contentId ->

                content.nameMap?.let { nameMap ->
                    contentNameDAO.insert(
                            nameMap.map {
                                (SGContentNameEntity(contentId, it.key, it.value))
                            }
                    )
                }

                content.descriptionMap?.let { descriptionMap ->
                    contentDescriptionDAO.insert(
                            descriptionMap.map {
                                (SGContentDescriptionEntity(contentId, it.key.language, it.value))
                            }
                    )
                }

                content.serviceIdList?.let {
                    contentServiceIdDAO.insert(
                            it.map { serviceId ->
                                SGContentServiceIdEntity(contentId, serviceId)
                            }
                    )
                }
            }
        }

        notifyUpdated?.invoke()
    }

    private fun storeContentList(contacts: Collection<SGContent>) {
        db.contentDAO().insert(
                contacts.mapNotNull { content ->
                    content.id?.let { id ->
                        SGContentEntity(id, content.icon, content.version)
                    }
                }
        )
    }
}