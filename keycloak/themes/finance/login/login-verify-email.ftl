<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
    <#if section = "form">
        <div style="margin-bottom: 1.5rem;">
            <h3 class="fp-section-title">
                <span class="fp-section-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/></svg>
                </span>
                ${msg("emailVerifyTitle")}
            </h3>
            <p class="fp-section-desc">${msg("emailVerifyInstruction1")}</p>
        </div>

        <div class="fp-code-info">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
            <span>${msg("emailVerifyInstruction2")}</span>
        </div>

    <#elseif section = "info">
        <div class="fp-resend-box">
            <p>${msg("emailVerifyInstruction3")}
                <a href="${url.loginAction}" class="fp-link">${msg("doClickHere")}</a>
            </p>
        </div>
    </#if>
</@layout.registrationLayout>
