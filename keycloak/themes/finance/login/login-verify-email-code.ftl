<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true displayMessage=!messagesPerField.exists('email_code'); section>
    <#if section = "form">
        <div style="margin-bottom: 1.5rem;">
            <h3 class="fp-section-title">
                <span class="fp-section-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="m4 4 7.07 17 2.51-7.39L21 11.07z"/></svg>
                </span>
                ${msg("emailVerify.title")}
            </h3>
            <p class="fp-section-desc">${msg("emailVerifyInstruction1", user.email)}</p>
        </div>

        <div class="fp-code-info">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/></svg>
            <span>${kcSanitize(msg("loginVerifyCode.recipientHint", user.email))?no_esc}</span>
        </div>

        <form id="kc-verify-email-code-form" action="${url.loginAction}" method="post">
            <div class="form-group">
                <label for="email_code">${msg("loginVerifyCode.label")}</label>
                <input
                    type="text"
                    id="email_code"
                    name="email_code"
                    class="fp-code-input"
                    autocomplete="one-time-code"
                    inputmode="numeric"
                    pattern="[0-9]*"
                    maxlength="6"
                    autofocus
                    placeholder="000000"
                />
                <#if messagesPerField.exists('email_code')>
                    <span class="alert alert-error" style="margin-top: 0.5rem; display: block;">
                        ${kcSanitize(messagesPerField.get('email_code'))?no_esc}
                    </span>
                </#if>
            </div>

            <div id="kc-form-buttons">
                <button type="submit" class="btn-primary">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
                    ${msg("loginVerifyCode.submit")}
                </button>
            </div>
        </form>
    <#elseif section = "info">
        <div class="fp-resend-box">
            <p>${msg("loginVerifyCode.resend")}
                <a href="${url.loginAction}" class="fp-link">${msg("loginVerifyCode.resendLink")}</a>
            </p>
        </div>
    </#if>
</@layout.registrationLayout>
