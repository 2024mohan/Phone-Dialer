package app.minimaldialer.core.model

/** A contact as seen by the app. Phone numbers are kept as entered in the address book. */
data class Contact(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val numbers: List<ContactNumber>,
    val photoUri: String? = null,
    val starred: Boolean = false,
) {
    val primaryNumber: ContactNumber? get() = numbers.firstOrNull { it.isPrimary } ?: numbers.firstOrNull()
}

data class ContactNumber(
    val number: String,
    val label: String? = null,
    val isPrimary: Boolean = false,
) {
    val normalized: String get() = PhoneNumbers.normalize(number)
}
