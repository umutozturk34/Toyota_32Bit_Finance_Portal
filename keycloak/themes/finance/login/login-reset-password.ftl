<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true displayMessage=!messagesPerField.existsError('username'); section>
    <#if section = "form">
        <div style="margin-bottom: 1.5rem;">
            <h3 class="fp-section-title">
                <span class="fp-section-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                </span>
                ${msg("emailForgotTitle")}
            </h3>
            <p class="fp-section-desc">${msg("emailInstruction")}</p>
        </div>

        <form id="kc-reset-password-form" action="${url.loginAction}" method="post">
            <div class="form-group">
                <label for="username">
                    <#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>
                </label>
                <input type="text" id="username" name="username" autofocus
                    value="${(auth.attemptedUsername!'')}"
                    placeholder="<#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>"
                    aria-invalid="<#if messagesPerField.existsError('username')>true</#if>" />
                <#if messagesPerField.existsError('username')>
                    <span class="alert alert-error" style="margin-top: 0.5rem; display: block;">${kcSanitize(messagesPerField.getFirstError('username'))?no_esc}</span>
                </#if>
            </div>

            <div id="kc-form-buttons" style="margin-top: 1.5rem;">
                <input type="submit" value="${msg("doSubmit")}" />
            </div>

            <div style="margin-top: 1rem; text-align: center;">
                <a href="${url.loginUrl}">${kcSanitize(msg("backToLogin"))?no_esc}</a>
            </div>
        </form>
    <#elseif section = "info">
        ${msg("emailInstruction")}
    </#if>
</@layout.registrationLayout>
