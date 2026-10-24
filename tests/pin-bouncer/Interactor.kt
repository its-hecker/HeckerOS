// Test facades for platform storage and credential verification. Native flow and
// guard bodies are inserted unchanged from the prepared immutable framework.
package com.android.systemui.bouncer.domain.interactor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.*
import com.android.systemui.authentication.shared.model.AuthenticationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

class LockPatternUtils {
    val enabled = mutableMapOf(0 to true, 1 to true)
    val lengths = mutableMapOf(0 to -1, 1 to 4)
    fun isAutoPinConfirmEnabled(userId: Int) = enabled.getValue(userId)
    fun getPinLength(userId: Int) = lengths.getValue(userId)
    companion object { const val MIN_LOCK_PASSWORD_SIZE = 4 }
}
interface FeatureSettings { val isAutoConfirmFeatureEnabled: StateFlow<Boolean> }
class SettingsRepository(val lockPatternUtils: LockPatternUtils) : FeatureSettings {
    var userId = 0
    private lateinit var value: MutableStateFlow<Boolean>
    private lateinit var refreshValue: suspend (Int) -> Boolean
    private fun refreshingFlow(initialValue: Boolean, getFreshValue: suspend (Int) -> Boolean): StateFlow<Boolean> {
        value = MutableStateFlow(initialValue)
        refreshValue = getFreshValue
        return value
    }
    suspend fun refresh(userId: Int = this.userId) {
        this.userId = userId
        value.value = refreshValue(userId)
    }
/* NATIVE:SettingsGate.kt */
}
class Repository(val settings: SettingsRepository, val correctPin: List<Int>) {
    val isAutoConfirmFeatureEnabled = settings.isAutoConfirmFeatureEnabled
    val hasLockoutOccurred = MutableStateFlow(false)
    var lockoutEndTime: Long? = null
    val minPatternLength = 4
    val minPasswordLength = 4
    suspend fun getPinLength() = settings.lockPatternUtils.getPinLength(settings.userId)
}
class BouncerInteractor(val repository: Repository, private val applicationScope: CoroutineScope) {
    var processed = 0
    var credentialChecks = 0
    var failedAttempts = 0
    fun onDown() {}
/* NATIVE:AutoConfirm.kt */
/* NATIVE:AuthenticationGuards.kt */
    suspend fun skip(method: AuthenticationMethodModel, auto: Boolean, length: Int) =
        shouldSkipAuthenticationAttempt(method, auto, length)
    suspend fun authenticate(input: List<Any>, tryAutoConfirm: Boolean): AuthenticationResult {
        try {
            if (shouldSkipAuthenticationAttempt(Pin, tryAutoConfirm, input.size)) return AuthenticationResult.SKIPPED
            credentialChecks++
            // A fake verifier compares the whole synthetic credential. The guard
            // never decides success, and a wrong PIN must remain a failure.
            if (input == repository.correctPin) return AuthenticationResult.SUCCEEDED
            failedAttempts++
            return AuthenticationResult.FAILED
        } finally { processed++ }
    }
}
