package com.rate.sdk.ui.buyonline.model

import kotlinx.serialization.Serializable

/**
 * UI-side journey models for the buy-online flow.
 *
 * RELOCATED from the monolith's
 * `com.rate.aegis.customer.buyonline.model.BuyOnlineModels` — these are the
 * presentation-layer view models the 22 screens bind to (member selection chips,
 * personal-detail forms, lifestyle/medical questionnaires, KYC method picker,
 * bank-detail capture, the local proposal-result holder). The wire DTOs the VM
 * actually ships live in sdk-proposal (`network.BuyOnlineDtos`,
 * `model.journey.*`) and sdk-quoting (`network.QuoteDtos`); these stay UI-only.
 */

enum class MemberType { SELF, SPOUSE, KIDS, MORE }

@Serializable
data class JourneyMember(
    val id: String,
    val label: String,
    val age: Int = 0,
    val gender: String = "Male",
)

/**
 * Customer-facing plan tiers shown on the Quote screen. Maps to the actuarial
 * plan id + add-on cover bundle in [BuyOnlinePlanMapping].
 */
enum class PlanTier(val displayName: String) {
    PREMIER("Premier"), SIGNATURE("Signature"), GLOBAL("Global")
}

@Serializable
data class PlanQuote(
    val tier: String,
    val sumInsured: Long,
    val tenure: Int,
    val annualPremium: Double,
    val monthlyPremium: Double,
    val gstPercent: Double = 0.0,
)

@Serializable
data class AddOn(
    val id: String,
    val name: String,
    val description: String,
    val annualCost: Double,
)

@Serializable
data class PersonalDetail(
    val memberId: String,
    val label: String = "",
    val title: String = "Mr",
    val firstName: String = "",
    val lastName: String = "",
    val dob: String = "",
    val mobile: String = "",
    val email: String = "",
    val heightFt: String = "",
    val heightIn: String = "",
    val weightKg: String = "",
    val panNumber: String = "",
    val occupation: String = "",
)

data class LifestyleAnswers(
    val smokesMembers: Set<String> = emptySet(),
    val alcoholMembers: Set<String> = emptySet(),
    val tobaccoMembers: Set<String> = emptySet(),
)

data class MedicalQuestion(
    val id: String,
    val text: String,
    val requiresDetail: Boolean = false,
)

val MEDICAL_QUESTIONS = listOf(
    MedicalQuestion("ped",         "Do you have any pre-existing diseases (Diabetes, Hypertension, Thyroid etc.)?"),
    MedicalQuestion("physical",    "Do you have any physical disorder?"),
    MedicalQuestion("thyroid",     "Do you have any thyroid disorder?"),
    MedicalQuestion("ongoing",     "Do you have any ongoing diagnosis or treatment?"),
    MedicalQuestion("cholesterol", "Do you have any cholesterol-related issues?"),
    MedicalQuestion("heart",       "Do you have any heart or blood-pressure related issues?"),
    MedicalQuestion("hosp",        "Have you ever been hospitalised or undergone surgery in the last 4 years?", requiresDetail = true),
    MedicalQuestion("respiratory", "Do you have any respiratory condition (Asthma, COPD etc.)?"),
    MedicalQuestion("liver",       "Do you have any liver/kidney/urinary disorders?"),
    MedicalQuestion("hypertension","Do you have hypertension?"),
    MedicalQuestion("diabetes",    "Do you have Diabetes Mellitus?"),
    MedicalQuestion("pregnancy",   "Is any member pregnant or planning pregnancy?"),
    MedicalQuestion("family",      "Do your parents have history of heart disease/stroke/cancer/kidney failure?"),
    MedicalQuestion("disability",  "Do you have any disability?"),
    MedicalQuestion("declined",    "Has any proposal/policy been declined, deferred or cancelled?", requiresDetail = true),
)

enum class KycMethod(val displayName: String, val description: String) {
    CKYC("C-KYC",       "PAN and DOB based KYC verification"),
    EKYC("E-KYC",       "Aadhaar and OTP based KYC via DigiLocker"),
    MANUAL("Manual KYC","Upload identity and address proof documents"),
}

@Serializable
data class BankDetails(
    val accountNumber: String = "",
    val bankName: String = "",
    val ifscCode: String = "",
)

@Serializable
data class ApplicationResult(
    val proposalNumber: String,
    val planTier: String,
    val sumInsured: Long,
    val annualPremium: Double,
    val status: String = "Under Review",
)
