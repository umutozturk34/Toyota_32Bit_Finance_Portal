<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('totp'); section>
    <#if section = "form">
        <div style="margin-bottom: 1.5rem;">
            <h3 class="fp-section-title">
                <span class="fp-section-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
                </span>
                ${msg("loginTotpTitle")}
            </h3>
            <p class="fp-section-desc">${msg("loginOtp.subtitle")}</p>
        </div>

        <form id="kc-otp-login-form" action="${url.loginAction}" method="post">
            <#if otpLogin.userOtpCredentials?size gt 1>
                <div class="form-group">
                    <#list otpLogin.userOtpCredentials as otpCredential>
                        <div class="checkbox" style="margin-bottom: 0.5rem;">
                            <label>
                                <input type="radio" id="kc-otp-credential-${otpCredential?index}"
                                    name="selectedCredentialId" value="${otpCredential.id}"
                                    <#if otpCredential.id == otpLogin.selectedCredentialId>checked="checked"</#if>>
                                ${otpCredential.userLabel}
                            </label>
                        </div>
                    </#list>
                </div>
            </#if>

            <div class="form-group">
                <label for="otp">${msg("loginOtpOneTime")}</label>
                <input type="text" id="otp" name="otp" autocomplete="off" autofocus
                    inputmode="numeric" pattern="[0-9]*" maxlength="8"
                    placeholder="${msg("loginOtpOneTime")}"
                    aria-invalid="<#if messagesPerField.existsError('totp')>true</#if>" />
                <#if messagesPerField.existsError('totp')>
                    <span class="alert alert-error" style="margin-top: 0.5rem; display: block;">${kcSanitize(messagesPerField.get('totp'))?no_esc}</span>
                </#if>
            </div>

            <div id="kc-form-buttons" style="margin-top: 1.5rem;">
                <input type="submit" value="${msg("doLogIn")}" />
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
