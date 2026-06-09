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
            <p class="fp-section-desc">${msg("loginTotpStep1")}</p>
        </div>

        <#if mode?? && mode = "manual">
            <div style="padding: 1rem; background: var(--fp-surface); border: 1px solid var(--fp-border); border-radius: var(--fp-radius-sm); margin-bottom: 1rem;">
                <p style="font-size: 0.8125rem; color: var(--fp-fg-muted); margin: 0 0 0.5rem 0;">${msg("loginTotpManualStep2")}</p>
                <p style="font-size: 0.875rem; font-weight: 600; color: var(--fp-fg); word-break: break-all; margin: 0;">${totp.totpSecretEncoded}</p>
                <p style="font-size: 0.8125rem; color: var(--fp-fg-muted); margin: 0.75rem 0 0 0;">${msg("loginTotpManualStep3")}</p>
                <ul style="margin: 0.5rem 0 0 0;">
                    <li>${msg("loginTotpType")}: ${msg("loginTotp." + totp.policy.type)}</li>
                    <li>${msg("loginTotpAlgorithm")}: ${totp.policy.getAlgorithmKey()}</li>
                    <li>${msg("loginTotpDigits")}: ${totp.policy.digits}</li>
                    <#if totp.policy.type = "totp">
                        <li>${msg("loginTotpInterval")}: ${totp.policy.period}</li>
                    <#elseif totp.policy.type = "hotp">
                        <li>${msg("loginTotpCounter")}: ${totp.policy.initialCounter}</li>
                    </#if>
                </ul>
                <a href="${totp.qrUrl}" style="display: inline-block; margin-top: 0.75rem; font-size: 0.8125rem;">${msg("loginTotpScanBarcode")}</a>
            </div>
        <#else>
            <div style="text-align: center; margin-bottom: 1rem;">
                <img src="data:image/png;base64, ${totp.totpSecretQrCode}" alt="QR Code" style="border-radius: var(--fp-radius-sm); border: 1px solid var(--fp-border); max-width: 200px;">
                <div style="margin-top: 0.5rem;">
                    <a href="${totp.manualUrl}" style="font-size: 0.8125rem;">${msg("loginTotpUnableToScan")}</a>
                </div>
            </div>
        </#if>

        <form id="kc-totp-settings-form" action="${url.loginAction}" method="post">
            <div class="form-group">
                <label for="totp">${msg("authenticatorCode")}</label>
                <input type="text" id="totp" name="totp" autocomplete="off" autofocus
                    inputmode="numeric" pattern="[0-9]*" maxlength="8"
                    placeholder="${msg("authenticatorCode")}"
                    aria-invalid="<#if messagesPerField.existsError('totp')>true</#if>" />
                <#if messagesPerField.existsError('totp')>
                    <span class="alert alert-error" style="margin-top: 0.5rem; display: block;">${kcSanitize(messagesPerField.get('totp'))?no_esc}</span>
                </#if>
            </div>

            <div class="form-group">
                <label for="userLabel">${msg("loginTotpDeviceName")}</label>
                <input type="text" id="userLabel" name="userLabel" autocomplete="off"
                    maxlength="80"
                    placeholder="${msg("loginTotpDeviceName")}" />
            </div>

            <input type="hidden" id="totpSecret" name="totpSecret" value="${totp.totpSecret}" />

            <div id="kc-form-buttons" style="margin-top: 1.5rem;">
                <#if isAppInitiatedAction??>
                    <input type="submit" value="${msg("doSubmit")}" />
                    <button type="submit" name="cancel-aia" value="true" class="btn-default" style="margin-top: 0.5rem;">${msg("doCancel")}</button>
                <#else>
                    <input type="submit" value="${msg("doSubmit")}" />
                </#if>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
