package com.impulsosocial.server.model

import com.impulsosocial.server.CREDICASH_APP_VERSION
data class ErrorResponse(
    val message: String,
    val code: String = "GENERIC_ERROR",
    val retryable: Boolean = false,
    val reference: String? = null
)
data class MessageResponse(val message: String)
data class HealthResponse(
    val status: String,
    val database: String,
    val version: String,
    val telegram: String = "unknown",
    val push: String = "unknown",
    val recaptcha: String = "unknown",
    val bcv: String = "unknown",
    val schema: String = "unknown",
    val detail: String? = null
)

data class RegisterRequest(
    val username: String,
    val firstName: String,
    val middleName: String,
    val lastName: String,
    val secondLastName: String,
    val email: String,
    val telegramUsername: String = "",
    val phone: String,
    val birthDate: String,
    val employmentType: String,
    val state: String? = null,
    val municipality: String? = null,
    val parish: String? = null,
    val community: String? = null,
    val address: String? = null,
    val password: String,
    val pin: String,
    val acceptedTerms: Boolean = false,
    val termsVersion: String? = null,
    val privacyVersion: String? = null,
    val recaptchaToken: String? = null,
    val captchaToken: String? = null
)

data class RegisterResponse(
    val userId: Long,
    val verificationStatus: String,
    val registrationToken: String? = null,
    val accountVerified: Boolean = false,
    val telegramVerificationRequired: Boolean = false,
    val telegramLinked: Boolean = false,
    val telegramLinkUrl: String? = null
)

data class BiometricCredentialRequest(
    val deviceIdHash: String,
    val publicKeyHash: String,
    val publicKeyBase64: String,
    val keyAlgorithm: String = "EC",
    val deviceName: String? = null,
    val appVersion: String? = null,
    val platform: String = "ANDROID"
)

data class BiometricCredentialResponse(
    val id: Long,
    val deviceIdHash: String,
    val publicKeyHash: String,
    val enabled: Boolean,
    val registeredAt: String
)

data class BiometricCredentialDisableRequest(
    val deviceIdHash: String
)

data class LoginRequest(
    val username: String? = null,
    val email: String? = null,
    val password: String,
    val recaptchaToken: String? = null,
    val captchaToken: String? = null
)
data class LoginResponse(
    val userId: Long,
    val verificationStatus: String,
    val email: String = "",
    val accountStatus: String = "ACTIVE",
    val suspensionReason: String? = null,
    val suspendedAt: String? = null,
    val pinChallengeToken: String? = null,
    val registrationToken: String? = null,
    val accountVerified: Boolean = true,
    val telegramVerificationRequired: Boolean = false,
    val telegramLinked: Boolean = false,
    val telegramLinkUrl: String? = null
)

data class TelegramVerificationRequest(
    val userId: Long,
    val email: String,
    val code: String? = null,
    val verificationCode: String? = null,
    val recaptchaToken: String? = null,
    val captchaToken: String? = null
)

data class TelegramResendRequest(
    val userId: Long,
    val email: String,
    val recaptchaToken: String? = null,
    val captchaToken: String? = null
)

data class PasswordResetRequest(
    val email: String? = null,
    val identifier: String? = null,
    val recaptchaToken: String? = null,
    val captchaToken: String? = null
)

data class PasswordResetConfirmRequest(
    val email: String? = null,
    val identifier: String? = null,
    val code: String? = null,
    val verificationCode: String? = null,
    val newPassword: String? = null,
    val password: String? = null,
    val recaptchaToken: String? = null,
    val captchaToken: String? = null
)

data class SecurityActionResponse(
    val message: String,
    val registrationToken: String? = null,
    val userId: Long? = null,
    val accountVerified: Boolean? = null,
    val telegramLinked: Boolean? = null,
    val telegramLinkUrl: String? = null
)

data class TelegramUpdate(
    val update_id: Long? = null,
    val message: TelegramMessage? = null
)

data class TelegramMessage(
    val message_id: Long? = null,
    val from: TelegramUser? = null,
    val chat: TelegramChat,
    val text: String? = null
)

data class TelegramUser(
    val id: Long,
    val is_bot: Boolean = false,
    val first_name: String? = null,
    val last_name: String? = null,
    val username: String? = null,
    val language_code: String? = null
)

data class TelegramChat(
    val id: Long,
    val type: String,
    val username: String? = null,
    val first_name: String? = null,
    val last_name: String? = null
)

data class VerifyPinRequest(
    val userId: Long,
    val pin: String,
    val challengeToken: String,
    val deviceIdHash: String = "",
    val deviceName: String? = null,
    val appVersion: String? = null
)
data class VerifyPinResponse(val accessToken: String, val refreshToken: String, val user: UserDto)
data class SavedSessionPinChallengeRequest(val refreshToken: String)
data class SavedSessionPinChallengeResponse(val userId: Long, val pinChallengeToken: String)
data class RefreshSessionRequest(val refreshToken: String)
data class RefreshSessionResponse(val accessToken: String, val refreshToken: String, val user: UserDto)
data class PersistentSessionResponse(val refreshToken: String)

data class DeviceTokenRequest(
    val token: String,
    val deviceName: String? = null,
    val platform: String = "ANDROID",
    val tokenKind: String = "FCM_REGISTRATION_TOKEN"
)

data class ExchangeRateDto(
    val currency: String = "USD",
    val rate: Double,
    val date: String,
    val source: String
)

data class NotificationAttachmentDto(
    val label: String,
    val path: String
)

data class NotificationDto(
    val id: Long,
    val title: String,
    val body: String,
    val type: String,
    val createdAt: String,
    val details: Map<String, String> = emptyMap(),
    val attachments: List<NotificationAttachmentDto> = emptyList()
)

data class UserDto(
    val id: Long,
    val username: String = "",
    val fullName: String,
    val firstName: String? = null,
    val middleName: String? = null,
    val lastName: String? = null,
    val secondLastName: String? = null,
    val email: String,
    val phone: String? = null,
    val birthDate: String? = null,
    val role: String,
    val verificationStatus: String,
    val accountStatus: String,
    val documentType: String? = null,
    val documentNumber: String? = null,
    val documentNumberMasked: String? = null,
    val state: String? = null,
    val municipality: String? = null,
    val parish: String? = null,
    val community: String? = null,
    val address: String? = null,
    val employmentType: String? = null,
    val creditLevel: Int? = null,
    val creditLevelName: String? = null,
    val creditScorePercentage: Int? = null,
    val creditHistoryStatus: String? = null,
    val adminSubrole: String? = null,
    val personGroupId: String? = null,
    val accountKind: String? = null,
    val linkedAccountUserId: Long? = null,
    val suspensionReason: String? = null,
    val suspendedAt: String? = null,
    val suspendedBy: Long? = null,
    val createdByUserId: Long? = null,
    val createdByUsername: String? = null,
    val createdByName: String? = null,
    val registrationSource: String? = null,
    val createdAt: String? = null,
    val lastLoginAt: String? = null
)





data class AccountSuspensionRequest(
    val reason: String = "Falta de pago"
)

data class RoleMetricDto(
    val key: String,
    val title: String,
    val value: String,
    val subtitle: String = "",
    val status: String = "NEUTRAL"
)

data class RoleTaskDto(
    val id: String,
    val title: String,
    val description: String,
    val priority: String,
    val destination: String,
    val actionLabel: String,
    val count: Int = 1
)

data class RoleExperienceDto(
    val role: String,
    val subRole: String,
    val permissions: List<String>,
    val greeting: String,
    val metrics: List<RoleMetricDto>,
    val tasks: List<RoleTaskDto>,
    val alerts: List<String> = emptyList()
)

data class AdminUserDossierDto(
    val user: UserDto,
    val credit: CreditSummaryDto?,
    val purchases: List<PurchaseDto>,
    val paymentReports: List<AdminUserPaymentReportDto>,
    val loans: List<AdminCreditLoanDto>,
    val activeDevices: Int,
    val notificationCount: Int,
    val auditEventCount: Int
)

data class ReconciliationItemDto(
    val reportId: Long,
    val invoiceNumber: String,
    val username: String,
    val amountReportedBs: Double,
    val expectedAmountBs: Double,
    val referenceNumber: String,
    val bank: String,
    val paymentStatus: String,
    val reconciliationStatus: String,
    val confidencePercent: Int,
    val riskLevel: String,
    val recommendation: String,
    val createdAt: String
)

data class AccountantReconciliationDto(
    val total: Int,
    val matched: Int,
    val probable: Int,
    val manualReview: Int,
    val differences: Int,
    val duplicates: Int,
    val items: List<ReconciliationItemDto>
)

data class ReconciliationDecisionRequest(
    val status: String,
    val confidencePercent: Int = 100,
    val notes: String? = null
)

data class MonthlyCloseDto(
    val periodMonth: String,
    val status: String,
    val synchronizedMovements: Boolean,
    val reconciledPayments: Boolean,
    val invoicesRecorded: Boolean,
    val budgetReviewed: Boolean,
    val pendingDifferences: Int,
    val canClose: Boolean
)

data class MonthlyCloseRequest(val periodMonth: String)

data class SensitiveApprovalDto(
    val id: Long,
    val actionType: String,
    val entityType: String?,
    val entityId: Long?,
    val amountUsd: Double?,
    val description: String,
    val requestedBy: Long,
    val requesterName: String,
    val approvedBy: Long?,
    val approverName: String?,
    val status: String,
    val decisionNotes: String?,
    val createdAt: String,
    val reviewedAt: String?
)

data class CreateSensitiveApprovalRequest(
    val actionType: String,
    val entityType: String? = null,
    val entityId: Long? = null,
    val amountUsd: Double? = null,
    val description: String
)

data class SensitiveApprovalDecisionRequest(
    val approved: Boolean,
    val notes: String? = null
)

/** Solicitud para asignar el subrol operativo de un administrador. */
data class AdminSubroleUpdateRequest(
    val subRole: String
)

/** Credicash 7.2.2: alta directa de Administradores/Almacenistas por el Contador desde Android o PC. */
data class StaffAccountCreationRequest(
    val role: String,
    val firstName: String,
    val middleName: String? = null,
    val lastName: String,
    val secondLastName: String? = null,
    val phone: String,
    val birthDate: String,
    val state: String? = null,
    val municipality: String? = null,
    val parish: String? = null,
    val community: String? = null,
    val address: String? = null,
    val documentType: String = "NATIONAL_ID",
    val documentNumber: String,
    val operationalUsername: String,
    val operationalEmail: String,
    val operationalPassword: String,
    val operationalPin: String,
    val adminSubRole: String? = null,
    val createBeneficiaryAccess: Boolean = false,
    val beneficiaryUsername: String? = null,
    val beneficiaryEmail: String? = null,
    val beneficiaryPassword: String? = null,
    val beneficiaryPin: String? = null
)

data class StaffAccountCreationResultDto(
    val personGroupId: String,
    val operational: UserDto,
    val beneficiary: UserDto? = null,
    val message: String
)

/** Credenciales para añadir posteriormente un acceso Beneficiario a personal operativo existente. */
data class LinkedBeneficiaryAccessRequest(
    val username: String,
    val email: String,
    val password: String,
    val pin: String
)

/** Vista previa de una fila encontrada en un Excel de personal operativo. */
data class StaffExcelImportRowDto(
    val sheetName: String,
    val rowNumber: Int,
    val role: String,
    val fullName: String,
    val username: String,
    val email: String,
    val phone: String,
    val birthDate: String = "",
    val employmentType: String = "",
    val documentType: String = "",
    val documentNumber: String,
    val state: String = "",
    val municipality: String = "",
    val parish: String = "",
    val community: String = "",
    val address: String = "",
    val adminSubRole: String = "",
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class StaffExcelImportPreviewDto(
    val totalRows: Int,
    val validRows: Int,
    val invalidRows: Int,
    val ignoredSheets: List<String> = emptyList(),
    val rows: List<StaffExcelImportRowDto> = emptyList(),
    val message: String
)

data class StaffExcelImportResultDto(
    val importedCount: Int,
    val skippedCount: Int,
    val importedUsers: List<UserDto> = emptyList(),
    val message: String
)

data class WarehouseOrderStatusRequest(
    val status: String,
    val notes: String? = null
)

data class VerificationDto(
    val id: Long,
    val user: UserDto,
    val documentType: String,
    val documentNumber: String,
    val documentUrl: String,
    val backDocumentUrl: String? = null,
    val selfieUrl: String? = null,
    val status: String,
    val rejectionReason: String? = null,
    val submittedAt: String
)

data class ReviewVerificationRequest(val approved: Boolean, val rejectionReason: String? = null)

data class ProductDto(
    val id: Long,
    val name: String,
    val category: String,
    val unit: String,
    /** Precio vigente en bolívares, conservado para clientes anteriores. */
    val price: Double,
    val stock: Int,
    val emoji: String? = "📦",
    val priceUsd: Double = 0.0,
    val priceBs: Double = price,
    val bcvRate: Double = 0.0,
    val pricingMode: String = "UNIT",
    val details: String = ""
)

data class CreateProductRequest(
    val name: String,
    val category: String,
    val unit: String,
    val stock: Int,
    /** Precio maestro en dólares. `price` queda como compatibilidad temporal. */
    val priceUsd: Double? = null,
    val price: Double? = null,
    val pricingMode: String = "UNIT",
    val details: String? = null
)

/** Vista previa de una fila encontrada en un Excel de productos. */
data class ProductExcelImportRowDto(
    val sheetName: String,
    val rowNumber: Int,
    val name: String,
    val mainCategory: String,
    val classification: String,
    val brand: String,
    val unit: String,
    val pricingMode: String,
    val priceUsd: Double?,
    val stock: Int?,
    val minimumStock: Int?,
    val status: String = "Activo",
    val details: String = "",
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class ProductExcelImportPreviewDto(
    val totalRows: Int,
    val validRows: Int,
    val invalidRows: Int,
    val ignoredSheets: List<String> = emptyList(),
    val rows: List<ProductExcelImportRowDto> = emptyList(),
    val message: String
)

data class ProductExcelImportResultDto(
    val importedCount: Int,
    val skippedCount: Int,
    val importedProducts: List<ProductDto> = emptyList(),
    val message: String
)

data class UpdateProductPricingRequest(
    val priceUsd: Double,
    val pricingMode: String = "UNIT"
)

data class SetStockRequest(val stock: Int)

data class MobilePaymentDto(
    val bank: String,
    val phone: String,
    val identityNumber: String,
    val holderName: String
)

data class BankTransferDto(
    val bank: String,
    val accountType: String,
    val accountNumber: String,
    val identityNumber: String,
    val holderName: String
)

data class AssociatedBusinessSummaryDto(
    val id: Long,
    val commercialName: String,
    val legalName: String,
    val rif: String,
    val logoUrl: String? = null
)

data class AssociatedBusinessDto(
    val id: Long,
    val commercialName: String,
    val legalName: String,
    val rif: String,
    val logoUrl: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val active: Boolean = true,
    val paymentMode: String = "MOBILE_PAYMENT",
    val mobilePayment: MobilePaymentDto? = null,
    val bankTransfer: BankTransferDto? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
)

data class SaveAssociatedBusinessRequest(
    val commercialName: String,
    val legalName: String,
    val rif: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val paymentMode: String,
    val mobilePayment: MobilePaymentDto? = null,
    val bankTransfer: BankTransferDto? = null
)

data class AssociatedBusinessStatusRequest(val active: Boolean)

data class PaymentDestinationDto(
    val fairId: Long,
    val fairName: String,
    val paymentMode: String,
    val business: AssociatedBusinessSummaryDto? = null,
    val mobilePayment: MobilePaymentDto? = null,
    val bankTransfer: BankTransferDto? = null
)

data class FairProductOfferDto(
    val productId: Long,
    val price: Double,
    val imageUrl: String? = null
)

data class FairDto(
    val id: Long,
    val name: String,
    val place: String,
    val schedule: String,
    val description: String,
    val published: Boolean,
    val finalized: Boolean = false,
    val paymentMode: String,
    val business: AssociatedBusinessSummaryDto? = null,
    val mobilePayment: MobilePaymentDto? = null,
    val bankTransfer: BankTransferDto? = null,
    val coverUrl: String? = null,
    val productOffers: List<FairProductOfferDto> = emptyList()
)

data class SaveFairRequest(
    val name: String,
    val place: String,
    val schedule: String,
    val description: String = "",
    val published: Boolean = false,
    val paymentMode: String,
    val businessId: Long? = null,
    val mobilePayment: MobilePaymentDto? = null,
    val bankTransfer: BankTransferDto? = null,
    val coverUrl: String? = null,
    val productOffers: List<FairProductOfferDto> = emptyList()
)

data class PublishFairRequest(val published: Boolean)

data class CommunityDto(
    val id: Long,
    val name: String,
    val state: String? = null,
    val municipality: String,
    val parish: String,
    val families: Int
)

data class CreateCommunityRequest(
    val name: String,
    val state: String? = null,
    val municipality: String,
    val parish: String,
    val families: Int
)

data class CommunityCatalogDto(
    val name: String,
    val state: String,
    val municipality: String,
    val parish: String
)

data class ComboLineDto(val productId: Long, val quantity: Int, val extra: Boolean = false)
data class ComboDto(
    val id: Long,
    val name: String,
    val description: String,
    val lines: List<ComboLineDto>,
    val active: Boolean,
    val coverUrl: String? = null
)

data class CreateComboRequest(val name: String, val description: String, val lines: List<ComboLineDto>)

data class CommunityRequestDto(
    val id: Long,
    val communityId: Long,
    val comboQuantities: Map<Long, Int>,
    val status: String,
    val createdLabel: String
)

data class CreateCommunityRequestPayload(val communityId: Long, val quantities: Map<Long, Int>)
data class UpdateCommunityRequestStatusRequest(val status: String)

data class PurchaseItemRequest(val productId: Long, val quantity: Int)
data class PurchaseComboRequest(val comboId: Long, val quantity: Int)
data class CreatePurchaseRequest(
    val fairId: Long,
    val items: List<PurchaseItemRequest> = emptyList(),
    val comboItems: List<PurchaseComboRequest> = emptyList(),
    val paymentMethod: String,
    val paymentReference: String = "",
    val originBankCode: String = "",
    val originPhone: String = "",
    val paidFromDifferentPhone: Boolean = false,
    val proofPath: String? = null
)

data class BankDto(val code: String, val name: String)
data class PaymentProofUploadResponse(val path: String)

data class PaymentVerificationDto(
    val id: Long,
    val orderId: Long,
    val invoiceNumber: String,
    val buyerName: String,
    val buyerPhone: String,
    val bank: String,
    val originPhone: String,
    val referenceNumber: String,
    val proofUrl: String,
    val status: String,
    val createdAt: String
)

data class PaymentVerificationDecisionRequest(val approved: Boolean, val notes: String? = null)

data class AdminPaymentReviewDto(
    val paymentId: Long,
    val orderId: Long,
    val invoiceNumber: String,
    val userId: Long,
    val buyerName: String,
    val buyerPhone: String,
    val method: String,
    val bank: String,
    val originPhone: String,
    val referenceNumber: String,
    val proofUrl: String,
    val amountBs: Double,
    val itemCount: Int,
    val orderStatus: String,
    val paymentStatus: String,
    val transactionAt: String,
    val createdAt: String,
    val requiresProofReview: Boolean
)

/** Solicitud unificada para reportar pagos de pedidos o cuotas de Crédito Credicash. */
data class CreateUserPaymentReportRequest(
    val targetType: String,
    val orderId: Long? = null,
    val installmentId: Long? = null,
    val method: String,
    val originBankCode: String,
    val originPhone: String,
    val referenceNumber: String,
    val amountBs: Double,
    val paidFromDifferentPhone: Boolean = false,
    val proofPath: String,
    val notes: String? = null
)

data class PaymentFraudAssessmentDto(
    val riskScore: Int,
    val riskLevel: String,
    val confidencePercent: Int,
    val recommendation: String,
    val reasons: List<String>,
    val suggestions: List<String>,
    val algorithmVersion: String,
    val bankConfirmationRequired: Boolean = true
)

data class UserPaymentReportDto(
    val id: Long,
    val targetType: String,
    val orderId: Long?,
    val installmentId: Long?,
    val invoiceNumber: String,
    val installmentNumber: Int?,
    val method: String,
    val bank: String,
    val originPhone: String,
    val referenceNumber: String,
    val proofUrl: String,
    val amountReportedBs: Double,
    val expectedAmountBs: Double,
    val status: String,
    val assessment: PaymentFraudAssessmentDto,
    val bankConfirmed: Boolean,
    val adminNotes: String? = null,
    val createdAt: String,
    val reviewedAt: String? = null
)

data class AdminUserPaymentReportDto(
    val id: Long,
    val userId: Long,
    val username: String,
    val buyerName: String,
    val buyerPhone: String,
    val targetType: String,
    val orderId: Long?,
    val installmentId: Long?,
    val invoiceNumber: String,
    val installmentNumber: Int?,
    val method: String,
    val bank: String,
    val originPhone: String,
    val referenceNumber: String,
    val proofUrl: String,
    val amountReportedBs: Double,
    val expectedAmountBs: Double,
    val paidFromDifferentPhone: Boolean,
    val userNotes: String? = null,
    val status: String,
    val assessment: PaymentFraudAssessmentDto,
    val bankConfirmed: Boolean,
    val adminNotes: String? = null,
    val createdAt: String,
    val reviewedAt: String? = null
)

data class UserPaymentReportDecisionRequest(
    val approved: Boolean,
    val bankConfirmed: Boolean = false,
    val notes: String? = null
)

data class QrScanRequest(val rawPayload: String)
data class QrScanRecordDto(val id: Long, val checksum: String, val createdAt: String)


data class ScannedInvoiceRecordDto(
    val id: Long,
    val invoiceNumber: String,
    val purchaseId: Long?,
    val scannedBy: Long,
    val scannerName: String,
    val createdAt: String
)

data class InventoryDemandDto(
    val productId: Long,
    val productName: String,
    val unit: String,
    val stock: Int,
    val requestedIndividual: Int,
    val requestedInCombos: Int,
    val totalRequested: Int,
    val pendingOrders: Int
)

data class CreditInstallmentDto(
    val id: Long,
    val loanId: Long,
    val installmentNumber: Int,
    val amountUsd: Double,
    val originalAmountBs: Double,
    val dueDate: String,
    val status: String,
    val invoiceNumber: String = "",
    val orderId: Long = 0,
    val fairId: Long = 0,
    val paymentDestination: PaymentDestinationDto? = null
)

data class CredimpulsoLevelRuleDto(
    val level: Int,
    val name: String,
    val completedPaymentsRequired: Int,
    val creditMultiplier: Int,
    val downPaymentPercent: Double,
    val baseAmountUsd: Double,
    val maxInstallments: Int = level.coerceIn(2, 6)
)

data class CreditSummaryDto(
    val level: Int,
    val walletAddress: String = "",
    val creditLimitUsd: Double,
    val usedUsd: Double,
    val availableUsd: Double,
    val status: String,
    val activeLoans: Int,
    val nextInstallment: CreditInstallmentDto? = null,
    val installments: List<CreditInstallmentDto> = emptyList(),
    val levelName: String = "Santa Ana",
    val completedPayments: Int = 0,
    val nextLevelAtPayments: Int? = null,
    val creditMultiplier: Int = 1,
    val downPaymentPercent: Double = 20.0,
    val baseAmountUsd: Double = 60.0,
    val maxInstallments: Int = 2,
    val creditScorePercentage: Int = 100,
    val latePaymentCount: Int = 0,
    val creditHistoryStatus: String = "ACTIVE",
    val creditSuspended: Boolean = false,
    val levelRules: List<CredimpulsoLevelRuleDto> = emptyList()
)

data class CreditHistoryEventDto(
    val id: Long,
    val userId: Long,
    val eventType: String,
    val scoreBefore: Int,
    val scoreAfter: Int,
    val invoiceNumber: String,
    val dueDate: String? = null,
    val occurredAt: String,
    val details: String? = null
)

data class AdminCreditHistoryDto(
    val userId: Long,
    val customerName: String,
    val email: String,
    val scorePercentage: Int,
    val latePaymentCount: Int,
    val status: String,
    val updatedAt: String,
    val events: List<CreditHistoryEventDto> = emptyList()
)

data class AdminCreditLoanDto(
    val id: Long,
    val userId: Long,
    val customerName: String,
    val invoiceNumber: String,
    val principalUsd: Double,
    val principalBs: Double,
    val bcvRate: Double,
    val status: String,
    val createdAt: String,
    val installments: List<CreditInstallmentDto> = emptyList()
)


data class CredimpulsoTransactionDto(
    val id: Long,
    val userId: Long,
    val customerName: String,
    val transactionType: String,
    val amountUsd: Double,
    val amountBs: Double,
    val bcvRate: Double,
    val balanceBeforeUsd: Double,
    val balanceAfterUsd: Double,
    val description: String,
    val invoiceNumber: String? = null,
    val loanId: Long? = null,
    val installmentId: Long? = null,
    val createdAt: String
)

data class PurchaseDto(
    val id: Long,
    val total: Double,
    val itemCount: Int,
    val status: String,
    val invoiceNumber: String,
    val createdAtMillis: Long,
    val paymentMethod: String? = null,
    val fairId: Long = 0,
    val paymentDestination: PaymentDestinationDto? = null
)

data class InvoiceLineDto(
    val productName: String,
    val unit: String,
    val quantity: Int,
    val unitPrice: Double
)

data class InvoiceDto(
    val purchaseId: Long,
    val invoiceNumber: String,
    val createdAtMillis: Long,
    val customerName: String,
    val customerEmail: String,
    val fairName: String,
    val fairPlace: String,
    val paymentMethod: String,
    val paymentReference: String,
    val paymentInstructions: String,
    val lines: List<InvoiceLineDto>,
    val total: Double,
    val customerFirstName: String = "",
    val customerMiddleName: String = "",
    val customerLastName: String = "",
    val customerSecondLastName: String = "",
    val customerBirthDate: String = "",
    val customerEmploymentType: String = "",
    val customerDocumentType: String = "",
    val customerDocument: String = "",
    val customerPhone: String = "",
    val customerState: String = "",
    val customerMunicipality: String = "",
    val customerParish: String = "",
    val customerCommunity: String = "",
    val customerAddress: String = "",
    val paymentOriginBank: String = "",
    val paymentOriginPhone: String = "",
    val integrity: InvoiceIntegrityDto = InvoiceIntegrityDto()
)




data class CreditDisbursementBankDto(
    val bankCode: String,
    val bankName: String,
    val accountType: String,
    val accountNumber: String,
    val holderName: String,
    val identityNumber: String
)

data class SaveCreditDisbursementBankRequest(
    val bankCode: String,
    val accountType: String,
    val accountNumber: String,
    val holderName: String,
    val identityNumber: String
)



data class AdminCredimpulsoWalletMovementDto(
    val id: Long,
    val type: String,
    val amountUsd: Double,
    val balanceBeforeUsd: Double,
    val balanceAfterUsd: Double,
    val userId: Long? = null,
    val userName: String? = null,
    val reference: String? = null,
    val description: String? = null,
    val createdAt: String
)

data class AdminCredimpulsoWalletDto(
    val walletAddress: String = "",
    val balanceUsd: Double,
    val balanceBs: Double = 0.0,
    val bcvRate: Double = 0.0,
    val bcvDate: String = "",
    val bcvSource: String = "",
    val totalTransferredUsd: Double,
    val lendingCapacityUsd: Double,
    val retainedBalanceUsd: Double = 0.0,
    val blocked: Boolean = false,
    val blockReason: String? = null,
    val evaluatedInstallments: Int = 0,
    val approvedInstallments: Int = 0,
    val evaluatedUsers: Int = 0,
    val evaluationLimit: Int = 3,
    val requiredApprovedInstallments: Int = 2,
    val movements: List<AdminCredimpulsoWalletMovementDto> = emptyList()
)

data class AdminWalletFundRequest(
    val amountUsd: Double,
    val description: String = "Fondos agregados a la cartera"
)

data class AdminWalletTransferRequest(
    val recipientUsername: String? = null,
    val username: String? = null,
    val recipientEmail: String? = null,
    val email: String? = null,
    val userId: Long? = null,
    val amountUsd: Double,
    val description: String = "Transferencia Crédito Credicash",
    val idempotencyKey: String? = null
)

data class CreditRequestCreateRequest(
    val amountUsd: Double,
    val installments: Int = 2,
    val purpose: String = "Compra de alimentos y productos"
)

data class CreditRequestDecisionRequest(
    val approved: Boolean,
    val lenderType: String? = null,
    val lenderBusinessId: Long? = null,
    val repaymentBusinessId: Long? = null,
    val disbursementDestinationType: String? = null
)

data class CreditRequestDto(
    val id: Long,
    val userId: Long,
    val customerName: String,
    val amountUsd: Double,
    val installments: Int,
    val purpose: String,
    val status: String,
    val createdAt: String,
    val transactionId: String? = null,
    val sourceWalletAddress: String? = null,
    val destinationWalletAddress: String? = null,
    val walletReference: String? = null,
    val approvedAmountBs: Double? = null,
    val approvalBcvRate: Double? = null,
    val disbursementBank: CreditDisbursementBankDto? = null,
    val lenderType: String? = null,
    val lenderBusiness: AssociatedBusinessSummaryDto? = null,
    val loanId: Long? = null,
    val invoiceNumber: String? = null,
    val disbursementDestinationType: String? = null,
    val paymentDestination: PaymentDestinationDto? = null
)


data class AccountantAdminDto(
    val id: Long,
    val username: String = "",
    val name: String,
    val email: String,
    val walletAddress: String = "",
    val walletBalanceUsd: Double
)

data class AccountantAllocationDto(
    val id: Long,
    val adminId: Long,
    val adminName: String,
    val amountUsd: Double,
    val amountBs: Double,
    val bcvRate: Double,
    val reference: String,
    val description: String? = null,
    val createdAt: String
)

data class AccountantWalletMovementDto(
    val id: Long,
    val type: String,
    val amountUsd: Double,
    val amountBs: Double,
    val bcvRate: Double,
    val balanceBeforeUsd: Double,
    val balanceAfterUsd: Double,
    val adminId: Long? = null,
    val adminName: String? = null,
    val reference: String? = null,
    val description: String? = null,
    val createdAt: String
)

data class BudgetMovementDto(
    val id: Long,
    val type: String,
    val amountUsd: Double,
    val amountBs: Double,
    val bcvRate: Double,
    val balanceBeforeUsd: Double,
    val balanceAfterUsd: Double,
    val reference: String,
    val description: String? = null,
    val expenseCategory: String? = null,
    val expenseCategoryLabel: String? = null,
    val createdAt: String
)

data class ExpenseCategorySummaryDto(
    val category: String,
    val label: String,
    val amountUsd: Double,
    val amountBs: Double
)

data class AdvancedBudgetDto(
    val bankBudgetUsd: Double = 0.0,
    val bankBudgetBs: Double = 0.0,
    val centralAvailableUsd: Double = 0.0,
    val centralAvailableBs: Double = 0.0,
    val administratorsAvailableUsd: Double = 0.0,
    val administratorsAvailableBs: Double = 0.0,
    val consolidatedAvailableUsd: Double = 0.0,
    val consolidatedAvailableBs: Double = 0.0,
    val investedUsd: Double = 0.0,
    val investedBs: Double = 0.0,
    val operatingExpensesUsd: Double = 0.0,
    val operatingExpensesBs: Double = 0.0,
    val administrativeExpensesUsd: Double = 0.0,
    val administrativeExpensesBs: Double = 0.0,
    val operatingExpenseBreakdown: List<ExpenseCategorySummaryDto> = emptyList(),
    val administrativeExpenseBreakdown: List<ExpenseCategorySummaryDto> = emptyList(),
    val totalExpensesUsd: Double = 0.0,
    val totalExpensesBs: Double = 0.0,
    val loansDisbursedUsd: Double = 0.0,
    val loansDisbursedBs: Double = 0.0,
    val loansRecoveredUsd: Double = 0.0,
    val loansRecoveredBs: Double = 0.0,
    val loansOutstandingUsd: Double = 0.0,
    val loansOutstandingBs: Double = 0.0,
    val overdueLoansUsd: Double = 0.0,
    val overdueLoansBs: Double = 0.0,
    val reservedUsd: Double = 0.0,
    val reservedBs: Double = 0.0,
    val totalCommittedUsd: Double = 0.0,
    val totalCommittedBs: Double = 0.0,
    val expectedCollections30DaysUsd: Double = 0.0,
    val expectedCollections30DaysBs: Double = 0.0,
    val projectedAvailable30DaysUsd: Double = 0.0,
    val projectedAvailable30DaysBs: Double = 0.0,
    val executionPercent: Double = 0.0,
    val recoveryPercent: Double = 0.0,
    val liquidityCoveragePercent: Double = 0.0,
    val integrityDifferenceUsd: Double = 0.0,
    val integrityDifferenceBs: Double = 0.0,
    val integrityStatus: String = "BALANCED",
    val activeLoans: Int = 0,
    val overdueLoans: Int = 0,
    val paidLoans: Int = 0,
    val calculatedAt: String = "",
    val movements: List<BudgetMovementDto> = emptyList()
)

data class BudgetMovementRequest(
    val type: String,
    val amountUsd: Double,
    val description: String = "Movimiento presupuestario",
    val expenseCategory: String? = null,
    val idempotencyKey: String? = null
)

data class AccountantWalletDto(
    val walletAddress: String = "",
    val initialBudgetUsd: Double,
    val balanceUsd: Double,
    val balanceBs: Double,
    val totalAllocatedUsd: Double,
    val totalAllocatedBs: Double,
    val bcvRate: Double,
    val bcvDate: String,
    val bcvSource: String,
    val fundingSource: String,
    val bankIntegrationStatus: String,
    val bankProvider: String? = null,
    val lastBankSyncAt: String? = null,
    val adminCount: Int = 0,
    val admins: List<AccountantAdminDto> = emptyList(),
    val allocations: List<AccountantAllocationDto> = emptyList(),
    val movements: List<AccountantWalletMovementDto> = emptyList(),
    val budget: AdvancedBudgetDto = AdvancedBudgetDto()
)

data class AccountantAllocationRequest(
    val adminUsername: String? = null,
    val adminEmail: String? = null,
    val email: String? = null,
    val adminId: Long? = null,
    val amountUsd: Double,
    val description: String = "Asignación presupuestaria a administrador",
    val idempotencyKey: String? = null
)

/**
 * Respuesta pública y sanitizada del visor de trazabilidad.
 * No expone nombres, correos, documentos, teléfonos ni identificadores internos de usuarios.
 * Este libro es privado y de solo lectura; no representa una blockchain pública.
 */
data class PublicLedgerTransactionDto(
    val sequenceNumber: Long,
    val transactionId: String,
    val reference: String,
    val operationType: String,
    val status: String,
    val sourceWalletAddress: String? = null,
    val destinationWalletAddress: String? = null,
    val amountUsd: Double,
    val amountBs: Double? = null,
    val bcvRate: Double? = null,
    val description: String,
    val createdAt: String,
    val completedAt: String? = null,
    val ledgerSource: String,
    val confirmationCount: Int = 0,
    val integrityStatus: String = "CONSISTENT",
    val integrityHash: String
)

data class PublicLedgerStatsDto(
    val totalTransactions: Long,
    val totalVolumeUsd: Double,
    val totalVolumeBs: Double,
    val walletCount: Long,
    val latestSequence: Long,
    val latestTransactionAt: String? = null,
    val smartContractCount: Int = 0,
    val confirmedCount: Long = 0,
    val pendingCount: Long = 0,
    val rejectedCount: Long = 0,
    val todayCount: Long = 0
)

data class PublicLedgerPageDto(
    val networkName: String = "Credicash",
    val networkType: String = "PRIVATE_LEDGER",
    val isPublicBlockchain: Boolean = false,
    val notice: String = "Libro contable privado y de solo lectura para trazabilidad operativa.",
    val generatedAt: String,
    val page: Int,
    val pageSize: Int,
    val totalItems: Long,
    val totalPages: Int,
    val stats: PublicLedgerStatsDto,
    val transactions: List<PublicLedgerTransactionDto>
)

/** Política SemVer visible de Credicash. */
data class VersionPolicyDto(
    val currentVersion: String = CREDICASH_APP_VERSION,
    val scheme: String = "MAJOR.MINOR.PATCH",
    val majorMeaning: String = "Cambios estructurales, módulos principales o contratos incompatibles.",
    val minorMeaning: String = "Funciones nuevas compatibles dentro de la misma generación.",
    val patchMeaning: String = "Correcciones, seguridad y optimizaciones sin cambiar contratos.",
    val releaseName: String = "Railway · PostgreSQL administrado"
)

data class PredictionFactorDto(
    val code: String,
    val label: String,
    val value: Double,
    val weight: Double,
    val impact: String,
    val explanation: String
)

data class SubjectPredictionDto(
    val subjectId: Long,
    val username: String,
    val displayName: String,
    val role: String,
    val paymentSuccessPercent: Double,
    val purchaseSuccessPercent: Double,
    val latePaymentProbabilityPercent: Double,
    val confidencePercent: Double,
    val riskLevel: String,
    val recommendedCreditLimitUsd: Double,
    val predictedNextPurchaseUsd: Double,
    val sampleSize: Int,
    val factors: List<PredictionFactorDto> = emptyList()
)

data class BudgetForecastPointDto(
    val horizonDays: Int,
    val expectedCollectionsUsd: Double,
    val expectedOperatingExpensesUsd: Double,
    val expectedBankIncomeUsd: Double,
    val expectedOverdueUsd: Double,
    val projectedAvailableUsd: Double,
    val confidencePercent: Double
)

data class PredictiveDashboardDto(
    val generatedAt: String,
    val modelVersion: String = "PREDICTIVE-6.0.0",
    val dataQuality: String,
    val collectionProbabilityPercent: Double,
    val defaultRiskPercent: Double,
    val liquidityRiskLevel: String,
    val confidencePercent: Double,
    val forecasts: List<BudgetForecastPointDto> = emptyList(),
    val users: List<SubjectPredictionDto> = emptyList(),
    val administrators: List<SubjectPredictionDto> = emptyList(),
    val alerts: List<String> = emptyList()
)

data class InvoiceIntegrityDto(
    val status: String = "PENDING",
    val integrityScore: Int = 0,
    val calculatedTotalBs: Double = 0.0,
    val differenceBs: Double = 0.0,
    val documentHash: String = "",
    val algorithmVersion: String = "INVOICE-6.0.0",
    val warnings: List<String> = emptyList(),
    val verifiedAt: String? = null
)

data class AdminInvoiceIntegrityDto(
    val purchaseId: Long,
    val invoiceNumber: String,
    val customerName: String,
    val totalBs: Double,
    val status: String,
    val integrityScore: Int,
    val differenceBs: Double,
    val algorithmVersion: String,
    val warnings: List<String> = emptyList(),
    val verifiedAt: String? = null,
    val createdAt: String
)

/** Resumen técnico de calidad operativa, sin alterar la navegación de la aplicación. */
data class InventoryIntegrityItemDto(
    val productId: Long,
    val productName: String,
    val recordedStock: Int,
    val movementStock: Int,
    val difference: Int,
    val minimumStock: Int,
    val lowStock: Boolean,
    val consistent: Boolean
)

data class OperationalQualitySummaryDto(
    val version: String = CREDICASH_APP_VERSION,
    val generatedAt: String,
    val pendingPaymentReports: Int,
    val highRiskPaymentReports: Int,
    val paymentAmountDifferences: Int,
    val pendingUserVerifications: Int,
    val inventoryInconsistencies: Int,
    val lowStockProducts: Int,
    val activeSessions: Int,
    val databaseReady: Boolean = true
)
