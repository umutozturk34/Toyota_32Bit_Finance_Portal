<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "form">
        <div style="margin-bottom: 1.5rem;">
            <h3 class="fp-section-title">
                <span class="fp-section-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
                </span>
                ${msg("errorTitle")}
            </h3>
            <p class="fp-section-desc">${msg("errorTitleHtml")?no_esc}</p>
        </div>

        <div class="alert alert-error">
            <span class="kc-feedback-text">${message.summary?no_esc}</span>
        </div>

        <#if skipLink??>
        <#else>
            <#if client?? && client.baseUrl?has_content>
                <div id="kc-form-buttons" style="margin-top: 1.5rem;">
                    <a href="${client.baseUrl}" class="btn-default" style="text-align: center; display: block; text-decoration: none;">${kcSanitize(msg("backToApplication"))?no_esc}</a>
                </div>
            </#if>
        </#if>
    </#if>
</@layout.registrationLayout>
