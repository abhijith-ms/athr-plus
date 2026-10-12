package io.ather.pro.domain.media

import io.ather.pro.service.GoogleMapsDisplayService
import io.ather.pro.service.MonitorNotification
import io.ather.pro.util.MediaProbe
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNotificationIdsTest {
    @Test fun chargingAndMediaForegroundNotificationsCannotOverwriteEachOther() {
        assertEquals(3, setOf(MonitorNotification.ID, MediaProbe.NOTIFICATION_ID, GoogleMapsDisplayService.NOTIFICATION_ID).size)
    }
}
