package com.android.systemui.bouncer.ui.viewmodel
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.*
import com.android.systemui.bouncer.domain.interactor.*
import com.android.systemui.bouncer.ui.helper.BouncerHapticPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

enum class ActionButtonAppearance { Hidden, Shown }
class PinHarness(val engine: BouncerInteractor) : AuthMethodBouncerViewModel(
    engine, MutableStateFlow(true), "test", BouncerHapticPlayer()
) {
    override val authenticationMethod = AuthenticationMethodModel.Pin
    override val lockoutMessageId = 0
    override val _readyToTryAuthenticate = MutableStateFlow(true)
    private val mutablePinInput = MutableStateFlow(PinInputViewModel.empty())
    private val hintedPinLength = MutableStateFlow<Int?>(null)
    private val _confirmButtonAppearance = MutableStateFlow(ActionButtonAppearance.Hidden)
    private val requests = Channel<Any>(Channel.BUFFERED)
    private object OnAuthenticateButtonClickedForSim
    val confirmAppearance get() = _confirmButtonAppearance.value
    var successes = 0
    fun onIntentionalUserInput() {}
    fun length() = getInput().size
    override fun getInput(): List<Any> = mutablePinInput.value.getPin()
    override fun clearInput() { mutablePinInput.value = mutablePinInput.value.clearAll() }
    override fun onSuccessfulAuthentication() { successes++ }
    override suspend fun onActivated(): Nothing = coroutineScope {
        launch {
/* NATIVE:ConfirmAppearance.kt */
        }
        super.onActivated()
    }
/* NATIVE:PinClick.kt */
/* NATIVE:ConfirmClick.kt */
}

suspend fun awaitCondition(condition: () -> Boolean) {
    withTimeout(3000) { while (!condition()) yield() }
}
suspend fun enter(vm: PinHarness, digits: List<Int>) {
    for (digit in digits) {
        val before = vm.engine.processed
        vm.onPinButtonClicked(digit)
        awaitCondition { vm.engine.processed > before }
        yield()
    }
}
suspend fun confirm(vm: PinHarness) {
    val before = vm.engine.processed
    vm.onAuthenticateButtonClicked()
    awaitCondition { vm.engine.processed > before }
    yield()
}
suspend fun scenario(length: Int, enabled: Boolean = true, body: suspend (PinHarness, Repository) -> Unit) = coroutineScope {
    val storage = LockPatternUtils()
    storage.lengths[0] = length
    storage.enabled[0] = enabled
    val settings = SettingsRepository(storage)
    check(!settings.isAutoConfirmFeatureEnabled.value) // Never trust a cached flag at startup.
    settings.refresh()
    val digits = List(if (length >= 4) length else 6) { (it + 1) % 10 }
    val repository = Repository(settings, digits)
    val engine = BouncerInteractor(repository, this)
    val vm = PinHarness(engine)
    val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.onActivated() }
    try {
        val eligible = enabled && length >= 4
        awaitCondition { engine.isAutoConfirmEnabled.value == eligible &&
            vm.confirmAppearance == if (eligible) ActionButtonAppearance.Hidden else ActionButtonAppearance.Shown }
        body(vm, repository)
    } finally {
        job.cancelAndJoin()
        coroutineContext.cancelChildren()
    }
}

fun main() = runBlocking {
    // Recreate pre-first-unlock metadata on consecutive boots, including an old
    // auto-confirm flag. Digits must remain until the user explicitly confirms.
    repeat(2) {
        for (length in listOf(-1, 0, 1, 3)) scenario(length) { vm, repo ->
            enter(vm, repo.correctPin)
            check(vm.length() == 6 && vm.engine.credentialChecks == 0)
            check(vm.engine.failedAttempts == 0 && !vm.animateFailure.value)
            confirm(vm)
            check(vm.successes == 1 && vm.engine.credentialChecks == 1)
        }
    }
    // Valid stored lengths retain auto-confirm at the complete credential only.
    for (length in listOf(4, 6, 8)) scenario(length) { vm, repo ->
        enter(vm, repo.correctPin.dropLast(1))
        check(vm.length() == length - 1 && vm.engine.credentialChecks == 0)
        enter(vm, repo.correctPin.takeLast(1))
        check(vm.successes == 1 && vm.engine.credentialChecks == 1)
    }
    scenario(6, enabled = false) { vm, repo ->
        enter(vm, repo.correctPin)
        check(vm.length() == 6 && vm.engine.credentialChecks == 0)
        confirm(vm)
        check(vm.successes == 1 && vm.engine.credentialChecks == 1)
    }
    for (length in listOf(-1, 6)) scenario(length) { vm, repo ->
        enter(vm, repo.correctPin.map { (it + 1) % 10 })
        if (length == -1) confirm(vm)
        check(vm.successes == 0 && vm.engine.credentialChecks == 1)
        check(vm.engine.failedAttempts == 1 && vm.length() == 0 && vm.animateFailure.value)
    }
    // Metadata can become invalid after the UI has hydrated its enabled flag.
    scenario(6) { vm, repo ->
        repo.settings.lockPatternUtils.lengths[0] = -1
        check(vm.engine.isAutoConfirmEnabled.value)
        enter(vm, repo.correctPin)
        check(vm.length() == 6 && vm.engine.credentialChecks == 0)
        repo.settings.refresh()
        awaitCondition { vm.confirmAppearance == ActionButtonAppearance.Shown }
        confirm(vm)
        check(vm.successes == 1)
    }
    scenario(-1) { vm, repo ->
        repo.settings.lockPatternUtils.lengths[0] = 6
        repo.settings.refresh()
        awaitCondition { vm.confirmAppearance == ActionButtonAppearance.Hidden }
        enter(vm, repo.correctPin)
        check(vm.successes == 1)
    }
    scenario(-1) { vm, repo ->
        repo.lockoutEndTime = 1
        enter(vm, repo.correctPin)
        confirm(vm)
        check(vm.engine.credentialChecks == 0 && vm.successes == 0)
        repo.lockoutEndTime = null
        check(vm.engine.skip(Password, false, 3))
        check(vm.engine.skip(Pattern, false, 3))
        check(!vm.engine.skip(Password, false, 4))
        check(!vm.engine.skip(Pattern, false, 4))
    }
    val storage = LockPatternUtils()
    val settings = SettingsRepository(storage)
    settings.refresh(0)
    check(!settings.isAutoConfirmFeatureEnabled.value)
    settings.refresh(1)
    check(settings.isAutoConfirmFeatureEnabled.value)
    settings.refresh(0)
    check(!settings.isAutoConfirmFeatureEnabled.value)
    println("PIN entry: reboot, unavailable/invalid lengths, explicit/automatic confirmation, wrong PIN, lockout, metadata refresh and user isolation PASS")
}
