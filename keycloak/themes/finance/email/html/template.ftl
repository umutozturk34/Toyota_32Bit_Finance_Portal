<#macro emailLayout title="" subtitle="">
<#assign theme = (user.attributes.themePreference[0])!"DARK">
<!DOCTYPE html>
<html lang="tr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <title>Finance Portal</title>
    <style>
        body { margin:0; padding:0; width:100%; -webkit-text-size-adjust:100%; -ms-text-size-adjust:100%; background:#0a0b10; color:#e6e9ef; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif; }
        table { border-spacing:0; mso-table-lspace:0pt; mso-table-rspace:0pt; }
        img { border:0; outline:none; text-decoration:none; -ms-interpolation-mode:bicubic; }
        a { color:inherit; text-decoration:none; }
        .num { font-family:'SF Mono','Monaco','Consolas','Courier New',monospace; }
        @media (max-width:600px) {
            .container { width:100% !important; padding:0 16px !important; }
            .hero-title { font-size:18px !important; }
        }

        .wrapper { background:#0a0b10; padding:32px 0; }
        .brand-row { padding:0 0 18px 4px; }
        .brand-dot { display:inline-block; width:8px; height:8px; border-radius:50%; background:#6366f1; vertical-align:middle; margin-right:8px; }
        .brand-text { font-size:13px; font-weight:600; letter-spacing:0.4px; color:#8a93a3; text-transform:uppercase; vertical-align:middle; }

        .card { width:560px; max-width:560px; background:#11141d; border:1px solid #1f2430; border-radius:16px; overflow:hidden; }
        .accent-strip { height:3px; background:linear-gradient(90deg,#6366f1,#a78bfa); font-size:0; line-height:0; }
        .hero { padding:28px 32px 8px; }
        .hero-title { margin:0; font-size:20px; font-weight:700; line-height:1.3; color:#e6e9ef; }
        .hero-sub { margin:6px 0 0; font-size:12px; color:#8a93a3; }
        .body-section { padding:18px 32px 26px; font-size:14px; line-height:1.7; color:#c9cfd9; }
        .body-section p { margin:0 0 14px; }
        .body-section p:last-child { margin-bottom:0; }
        .body-section strong { color:#e6e9ef; font-weight:600; }
        .body-section a { color:#a78bfa; }

        .cta-wrap { text-align:center; margin:18px 0 8px; }
        .email-cta { display:inline-block; padding:13px 28px; font-size:14px; font-weight:600; color:#ffffff !important; background:linear-gradient(90deg,#6366f1,#a78bfa); border-radius:10px; text-decoration:none; letter-spacing:0.2px; }

        .email-info-box { margin:14px 0; padding:14px 18px; background:#0e111a; border:1px solid #1f2430; border-radius:12px; }
        .email-info-box p { margin:0; font-size:13px; color:#8a93a3; line-height:1.6; }
        .email-info-box strong { color:#e6e9ef; }
        .email-info-box code { font-family:'SF Mono','Monaco','Consolas','Courier New',monospace; font-size:12px; color:#e6e9ef; background:#1c2030; padding:2px 8px; border-radius:6px; }

        .code-wrap { text-align:center; margin:18px 0 6px; }
        .email-code-box { display:inline-block; padding:18px 36px; background:linear-gradient(135deg,rgba(99,102,241,0.12),rgba(167,139,250,0.08)); border:1px solid #2c3142; border-radius:14px; }
        .email-code { font-family:'SF Mono','Monaco','Consolas','Courier New',monospace; font-size:32px; font-weight:700; letter-spacing:8px; color:#e6e9ef; }

        .footer-card { padding:18px 4px 0; }
        .footer-card p { margin:0 0 6px; font-size:12px; line-height:1.6; color:#5f6671; }
        .footer-card p:last-child { margin-bottom:0; font-size:11px; }
    </style>
    <#if theme == "LIGHT">
    <style>
        body { background:#f3f5fa !important; color:#1a1d24 !important; }
        .wrapper { background:#f3f5fa !important; }
        .brand-text { color:#5f6671 !important; }
        .card { background:#ffffff !important; border-color:#e5e9f0 !important; }
        .hero-title { color:#1a1d24 !important; }
        .hero-sub { color:#5f6671 !important; }
        .body-section { color:#3a4150 !important; }
        .body-section strong { color:#1a1d24 !important; }
        .body-section a { color:#6366f1 !important; }
        .email-info-box { background:#f9fafd !important; border-color:#e5e9f0 !important; }
        .email-info-box p { color:#5f6671 !important; }
        .email-info-box strong { color:#1a1d24 !important; }
        .email-info-box code { color:#1a1d24 !important; background:#eef0f5 !important; }
        .email-code-box { background:linear-gradient(135deg,rgba(99,102,241,0.08),rgba(167,139,250,0.05)) !important; border-color:#dde1ec !important; }
        .email-code { color:#1a1d24 !important; }
        .footer-card p { color:#8b94a3 !important; }
    </style>
    </#if>
</head>
<body>
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" class="wrapper">
    <tr>
        <td align="center">
            <table role="presentation" class="container" width="560" cellspacing="0" cellpadding="0" border="0" style="width:560px;max-width:560px;">
                <tr>
                    <td align="left" class="brand-row">
                        <span class="brand-dot"></span>
                        <span class="brand-text">Finance Portal</span>
                    </td>
                </tr>
            </table>

            <table role="presentation" class="container card" width="560" cellspacing="0" cellpadding="0" border="0">
                <tr><td class="accent-strip">&nbsp;</td></tr>
                <#if title?has_content>
                <tr>
                    <td class="hero">
                        <h1 class="hero-title">${title}</h1>
                        <#if subtitle?has_content><p class="hero-sub">${subtitle}</p></#if>
                    </td>
                </tr>
                </#if>
                <tr>
                    <td class="body-section">
                        <#nested>
                    </td>
                </tr>
            </table>

            <table role="presentation" class="container" width="560" cellspacing="0" cellpadding="0" border="0" style="width:560px;max-width:560px;">
                <tr>
                    <td class="footer-card">
                        <p>Bu e-posta Finance Portal tarafından otomatik olarak gönderilmiştir.</p>
                        <p>İçerik tanıdık değilse e-postayı yok sayabilirsin.</p>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
</table>
</body>
</html>
</#macro>
