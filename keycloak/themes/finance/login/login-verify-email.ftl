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

        <div style="padding: 1rem; background: var(--fp-surface); border: 1px solid var(--fp-border); border-radius: var(--fp-radius-sm); margin-bottom: 1rem; text-align: center;">
            <p style="font-size: 0.875rem; color: var(--fp-fg-muted); margin: 0 0 0.75rem 0;">${msg("emailVerifyInstruction2")}</p>
            <p style="font-size: 0.8125rem; color: var(--fp-fg-subtle); margin: 0;">${msg("emailVerifyInstruction3")}</p>
        </div>

        <div id="kc-form-buttons" style="margin-top: 1.5rem;">
            <a href="${url.loginAction}" class="btn-primary" style="text-align: center; display: block; text-decoration: none; padding: 0.625rem 1.25rem; font-size: 0.875rem; font-weight: 600; color: #fff; background: var(--fp-accent); border-radius: var(--fp-radius-sm);">
                ${msg("doClickHere")} ${msg("emailVerifyInstruction3")}
            </a>
        </div>
    </#if>
</@layout.registrationLayout>
