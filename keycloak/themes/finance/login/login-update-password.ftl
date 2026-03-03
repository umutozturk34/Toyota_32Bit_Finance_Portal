<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('password','password-confirm'); section>
    <#if section = "form">
        <div style="margin-bottom: 1.5rem;">
            <h3 class="fp-section-title">
                <span class="fp-section-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                </span>
                ${msg("updatePasswordTitle")}
            </h3>
            <p class="fp-section-desc">Choose a new secure password</p>
        </div>

        <form id="kc-passwd-update-form" action="${url.loginAction}" method="post">
            <input type="text" id="username" name="username" value="${username}" autocomplete="username" readonly="readonly" style="display:none;"/>
            <input type="password" id="password" name="password" autocomplete="current-password" style="display:none;"/>

            <div class="form-group">
                <label for="password-new">${msg("passwordNew")}</label>
                <input type="password" id="password-new" name="password-new" autofocus autocomplete="new-password"
                    placeholder="${msg("passwordNew")}"
                    aria-invalid="<#if messagesPerField.existsError('password','password-confirm')>true</#if>" />
                <#if messagesPerField.existsError('password')>
                    <span class="alert alert-error" style="margin-top: 0.5rem; display: block;">${kcSanitize(messagesPerField.get('password'))?no_esc}</span>
                </#if>
            </div>

            <div class="form-group">
                <label for="password-confirm">${msg("passwordConfirm")}</label>
                <input type="password" id="password-confirm" name="password-confirm" autocomplete="new-password"
                    placeholder="${msg("passwordConfirm")}"
                    aria-invalid="<#if messagesPerField.existsError('password-confirm')>true</#if>" />
                <#if messagesPerField.existsError('password-confirm')>
                    <span class="alert alert-error" style="margin-top: 0.5rem; display: block;">${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}</span>
                </#if>
            </div>

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
