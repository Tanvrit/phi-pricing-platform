package com.rate.sdk.party.handler

import com.rate.core.base.error.AppResult
import com.rate.core.base.error.DomainError
import com.rate.core.base.time.Now
import com.rate.core.base.util.ValidationResult
import com.rate.core.base.util.Validators
import com.rate.sdk.party.event.PartyEvent
import com.rate.sdk.party.event.PartyEventSink
import com.rate.sdk.party.model.BankAccount
import com.rate.sdk.party.model.GroupEmployer
import com.rate.sdk.party.model.PolicyHolder
import com.rate.sdk.party.model.RetailProposer
import com.rate.sdk.party.repository.PartyRepository

/**
 * Orchestrates party (proposer / employer) lifecycle: field validation (mobile / PAN /
 * Aadhaar / IFSC / pincode / GSTIN / email via the relocated core [Validators]),
 * de-duplication, persistence through [PartyRepository], and event emission.
 *
 * Pure orchestration — no IO of its own beyond the injected repository PORT, so it runs
 * identically on the server and (read paths) on the client.
 */
class PartyHandler(
    private val repository: PartyRepository,
    private val events: PartyEventSink = PartyEventSink.NOOP,
) {

    /** Validate a party without persisting. Empty list = valid. */
    fun validate(party: PolicyHolder): List<String> = when (party) {
        is RetailProposer -> validateProposer(party)
        is GroupEmployer -> validateEmployer(party)
    }

    private fun validateProposer(p: RetailProposer): List<String> = buildList {
        if (p.name.isBlank()) add("Proposer name is required")
        addError(Validators.mobile(p.mobile))
        p.email?.let { addError(Validators.email(it)) }
        p.pan?.let { addError(Validators.pan(it)) }
        if (p.address.pincode.isNotBlank()) addError(Validators.pincode(p.address.pincode))
        p.bankAccount?.let { addAll(validateBank(it)) }
        p.kyc.aadhaarLast4?.let { last4 ->
            if (last4.length != 4 || !last4.all(Char::isDigit)) add("Aadhaar last-4 must be 4 digits")
        }
    }

    private fun validateEmployer(e: GroupEmployer): List<String> = buildList {
        if (e.companyName.isBlank()) add("Company name is required")
        addError(Validators.mobile(e.mobile))
        e.email?.let { addError(Validators.email(it)) }
        e.gstin?.let { addError(Validators.gstin(it)) }
        e.pan?.let { addError(Validators.pan(it)) }
        if (e.address.pincode.isNotBlank()) addError(Validators.pincode(e.address.pincode))
        if (e.registeredOfficeAddress.pincode.isNotBlank()) {
            addError(Validators.pincode(e.registeredOfficeAddress.pincode))
        }
        if (e.estimatedLives < 0) add("Estimated lives cannot be negative")
    }

    private fun validateBank(b: BankAccount): List<String> = buildList {
        if (b.accountHolderName.isBlank()) add("Account holder name is required")
        addError(Validators.accountNumber(b.accountNumber))
        addError(Validators.ifsc(b.ifsc))
    }

    /** Standalone Aadhaar check (KYC step collects the full number, never stored raw). */
    fun validateAadhaar(aadhaar: String?): ValidationResult = Validators.aadhaar(aadhaar)

    /**
     * Validate then create a party. Rejects duplicates by mobile (and PAN when present).
     */
    suspend fun create(party: PolicyHolder, actor: String?): AppResult<PolicyHolder> {
        val errors = validate(party)
        if (errors.isNotEmpty()) return AppResult.Err(DomainError.Validation(errors))

        repository.getByMobile(party.mobile)?.let {
            return AppResult.Err(DomainError.Conflict("A party already exists for mobile ${party.mobile}"))
        }
        panOf(party)?.let { pan ->
            repository.getByPan(pan)?.let {
                return AppResult.Err(DomainError.Conflict("A party already exists for PAN $pan"))
            }
        }

        val saved = repository.create(party, actor)
        events.emit(PartyEvent.PartyCreated(saved.id, saved.productLine, actor, Now.instant()))
        return AppResult.Ok(saved)
    }

    /** Validate then optimistically update an existing party. */
    suspend fun update(party: PolicyHolder, expectedV: Long, actor: String?): AppResult<PolicyHolder> {
        val errors = validate(party)
        if (errors.isNotEmpty()) return AppResult.Err(DomainError.Validation(errors))
        val saved = repository.update(party, expectedV, actor)
        events.emit(PartyEvent.PartyUpdated(saved.id, saved.productLine, actor, Now.instant()))
        return AppResult.Ok(saved)
    }

    private fun panOf(party: PolicyHolder): String? = when (party) {
        is RetailProposer -> party.pan
        is GroupEmployer -> party.pan
    }

    private fun MutableList<String>.addError(r: ValidationResult) {
        (r as? ValidationResult.Invalid)?.let { add(it.message) }
    }
}
