package com.rrajath.grove.capture

/** Validation state for one [CaptureTemplate]: target-filename + placeholder checks. */
data class TemplateIssues(
    /** [CaptureTemplate.targetFile] error (PLAIN kind only; always null for ROAM_NODE). */
    val filenameError: String? = null,
    /** [CaptureTemplate.roamDirectory] error (ROAM_NODE kind only). */
    val directoryError: String? = null,
    /** [CaptureTemplate.filenamePattern] error (ROAM_NODE kind only). */
    val filenamePatternError: String? = null,
    /** Distinct unsupported `%...` tokens found in the template body. */
    val invalidPlaceholders: List<String>,
) {
    val count: Int get() =
        listOfNotNull(filenameError, directoryError, filenamePatternError).size + invalidPlaceholders.size
    val hasErrors: Boolean get() = count > 0
}

object TemplateValidator {
    fun validate(template: CaptureTemplate): TemplateIssues = when (template.kind) {
        TemplateKind.PLAIN -> validate(template.targetFile, template.template)
        TemplateKind.ROAM_NODE -> TemplateIssues(
            directoryError = FilenameValidation.errorForDirectory(template.roamDirectory),
            filenamePatternError = FilenamePattern.errorFor(template.filenamePattern),
            invalidPlaceholders =
                PlaceholderExpander.findInvalid(template.newFileTemplate).map { it.token }.distinct(),
        )
    }

    fun validate(targetFile: String, templateText: String): TemplateIssues = TemplateIssues(
        filenameError = FilenameValidation.errorFor(targetFile),
        invalidPlaceholders = PlaceholderExpander.findInvalid(templateText).map { it.token }.distinct(),
    )
}
