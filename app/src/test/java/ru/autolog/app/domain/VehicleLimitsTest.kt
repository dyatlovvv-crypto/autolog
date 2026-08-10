package ru.autolog.app.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VehicleLimitsTest {

    @Test
    fun maxIsTen() {
        assertThat(VehicleLimits.MAX).isEqualTo(10)
        assertThat(VehicleLimits.MIN).isEqualTo(1)
    }

    @Test
    fun canAddUntilLimit() {
        assertThat(VehicleLimits.canAdd(0)).isTrue()
        assertThat(VehicleLimits.canAdd(9)).isTrue()
        assertThat(VehicleLimits.canAdd(10)).isFalse()
        assertThat(VehicleLimits.canAdd(11)).isFalse()
    }
}
