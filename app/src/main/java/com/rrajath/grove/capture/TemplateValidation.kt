package com.rrajath.grove.capture

/** Validation state for one [CaptureTemplate]: target-filename + placeholder checks. */
data class TemplateIssues(
    val filenameError: String?,
    /** Distinct unsupported `%...` tokens found in the template body. */
    val invalidPlaceholders: List<String>,
) {
    val count: Int get() = (if (filenameError != null) 1 else 0) + invalidPlaceholders.size
    val hasErrors: Boolean get() = count > 0
}

object TemplateValidator {
    fun validate(template: CaptureTemplate): TemplateIssues =
        validate(template.targetFile, template.template)

    fun validate(targetFile: String, templateText: String): TemplateIssues = TemplateIssues(
        filenameError = FilenameValidation.errorFor(targetFile),
        invalidPlaceholders = PlaceholderExpander.findInvalid(templateText).map { it.token }.distinct(),
    )
}
