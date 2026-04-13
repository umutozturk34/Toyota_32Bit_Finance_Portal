<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html<#if realm.internationalizationEnabled> lang="${locale.currentLanguageTag}"</#if> data-theme="dark">
<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <#if properties.meta?has_content>
        <#list properties.meta?split(' ') as meta>
            <meta name="${meta?split('==')[0]}" content="${meta?split('==')[1]}"/>
        </#list>
    </#if>
    <title>${msg("loginTitle",(realm.displayName!''))}</title>
    <link rel="icon" type="image/svg+xml" href="${url.resourcesPath}/img/favicon.svg" />
    <#if properties.stylesCommon?has_content>
        <#list properties.stylesCommon?split(' ') as style>
            <link href="${url.resourcesCommonPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
</head>
<body>
    <div class="fp-bg-orbs" aria-hidden="true" id="fp-orbs">
        <div class="orb-1"></div>
        <div class="orb-2"></div>
        <div class="orb-3"></div>
    </div>
    <div class="fp-grid-bg" id="fp-grid" aria-hidden="true"></div>

    <button class="fp-theme-toggle" id="fp-theme-btn" type="button" aria-label="Toggle theme">
        <svg id="fp-icon-sun" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display:none"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>
        <svg id="fp-icon-moon" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
    </button>

    <div class="login-pf-page">
        <div class="card-pf">
            <div class="fp-glow" id="fp-glow"></div>

            <div class="fp-card-header">
                <div class="fp-logo">
                    <span class="fp-logo-icon">
                        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/></svg>
                    </span>
                    <span class="fp-logo-text">Finance Portal</span>
                </div>
                <p>Secure Authentication Portal</p>
            </div>

            <div class="fp-card-body">
                <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                    <div class="alert alert-${message.type}">
                        <span class="kc-feedback-text">${kcSanitize(message.summary)?no_esc}</span>
                    </div>
                </#if>

                <#nested "form">

                <#if displayInfo>
                    <div style="margin-top: 1rem;">
                        <#nested "info">
                    </div>
                </#if>
            </div>

            <div class="fp-card-footer">
                <p>Powered by Keycloak & Spring Security</p>
            </div>
        </div>
    </div>

    <script>
        (function() {
            function getCookie(name) {
                var match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
                return match ? match[1] : null;
            }
            function setCookie(name, val) {
                document.cookie = name + '=' + val + ';path=/;max-age=31536000;SameSite=Lax';
            }
            var theme = getCookie('finance-theme') || localStorage.getItem('finance-theme');
            theme = (theme === 'light') ? 'light' : 'dark';
            document.documentElement.setAttribute('data-theme', theme);
            localStorage.setItem('finance-theme', theme);
            setCookie('finance-theme', theme);
            updateIcons(theme);

            document.getElementById('fp-theme-btn').addEventListener('click', function() {
                var current = document.documentElement.getAttribute('data-theme');
                var next = current === 'dark' ? 'light' : 'dark';
                document.documentElement.setAttribute('data-theme', next);
                localStorage.setItem('finance-theme', next);
                setCookie('finance-theme', next);
                updateIcons(next);
            });

            function updateIcons(t) {
                document.getElementById('fp-icon-sun').style.display = t === 'light' ? 'block' : 'none';
                document.getElementById('fp-icon-moon').style.display = t === 'dark' ? 'block' : 'none';
            }
        })();
    </script>
</body>
</html>
</#macro>
