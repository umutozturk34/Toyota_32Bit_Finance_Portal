<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
    <#if section = "form">
        <div style="margin-bottom: 1.5rem;">
            <h3 class="fp-section-title">
                <span class="fp-section-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
                </span>
                ${msg("pageExpiredTitle")}
            </h3>
        </div>

        <div id="kc-info-message">
            <p>${msg("pageExpiredMsg1")} <a id="loginRestartLink" href="${url.loginRestartFlowUrl}">${msg("doClickHere")}</a></p>
            <p>${msg("pageExpiredMsg2")} <a id="loginContinueLink" href="${url.loginAction}">${msg("doClickHere")}</a></p>
        </div>
    </#if>
</@layout.registrationLayout>
