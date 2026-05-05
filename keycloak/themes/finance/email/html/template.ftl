<#macro emailLayout title="" eyebrow="" subtitle="">
<#assign theme = (user.attributes.themePreference[0])!"DARK">
<!DOCTYPE html>
<html lang="tr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="color-scheme" content="dark light">
    <meta name="supported-color-schemes" content="dark light">
    <title>Finance Portal</title>
    <style>
        body { margin:0; padding:0; width:100%; -webkit-text-size-adjust:100%; -ms-text-size-adjust:100%; background:#070912; color:#dde3ee; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,Arial,sans-serif; }
        table { border-spacing:0; mso-table-lspace:0pt; mso-table-rspace:0pt; }
        img { border:0; outline:none; text-decoration:none; -ms-interpolation-mode:bicubic; display:block; }
        a { color:inherit; text-decoration:none; }

        @media (max-width:620px) {
            .container { width:100% !important; padding-left:18px !important; padding-right:18px !important; }
            .hero-title { font-size:22px !important; }
            .email-code { font-size:28px !important; letter-spacing:6px !important; }
            .hero-pad { padding-left:24px !important; padding-right:24px !important; }
            .body-pad { padding-left:24px !important; padding-right:24px !important; }
        }

        .wrapper { background:#070912; padding:40px 0 56px; }

        .ticker-row { padding:0 0 18px 2px; }
        .ticker-cell { font-family:'SFMono-Regular','Menlo','Consolas',monospace; font-size:10px; letter-spacing:1.6px; color:#5c6577; text-transform:uppercase; }
        .ticker-mark { display:inline-block; width:6px; height:6px; background:#6366f1; vertical-align:middle; margin:0 8px 1px 0; border-radius:1px; }
        .ticker-divider { color:#3a4254; padding:0 8px; }

        .card { width:600px; max-width:600px; background:#0d111c; border:1px solid #1a2030; border-radius:18px; overflow:hidden; }
        .accent-strip { height:2px; background:#6366f1; mso-line-height-rule:exactly; line-height:2px; font-size:0; }
        .accent-strip-grad { background:linear-gradient(90deg,#6366f1 0%,#8b5cf6 50%,#a78bfa 100%); }

        .hero { padding:36px 40px 12px; }
        .hero-pad { padding:36px 40px 12px; }
        .eyebrow { font-family:'SFMono-Regular','Menlo','Consolas',monospace; font-size:10px; font-weight:600; letter-spacing:2.4px; color:#8b8fa3; text-transform:uppercase; margin:0 0 14px; padding:0; }
        .hero-title { margin:0; font-family:Georgia,'Times New Roman',serif; font-size:26px; font-weight:600; line-height:1.25; color:#f0f2f7; letter-spacing:-0.4px; }
        .hero-sub { margin:8px 0 0; font-size:13px; font-style:italic; color:#8b93a8; line-height:1.5; }

        .body-section { padding:18px 40px 8px; font-size:14px; line-height:1.75; color:#bdc4d2; }
        .body-pad { padding:18px 40px 8px; }
        .body-section p { margin:0 0 14px; padding:0; }
        .body-section p:last-child { margin-bottom:0; }
        .body-section strong { color:#f0f2f7; font-weight:600; }
        .body-section a { color:#a78bfa; }

        .divider { height:1px; background:#1a2030; mso-line-height-rule:exactly; line-height:1px; font-size:0; margin:0 40px; }

        .cta-wrap { padding:6px 40px 8px; }
        .cta-table { border-spacing:0; }
        .cta-cell { background:#5b5ef0; border-radius:10px; mso-padding-alt:14px 28px; box-shadow:0 6px 22px -4px rgba(99,102,241,0.45); }
        .email-cta { display:inline-block; padding:14px 28px; font-size:14px; font-weight:600; color:#ffffff !important; text-decoration:none; letter-spacing:0.2px; line-height:1; }

        .info-box { padding:14px 40px 4px; }
        .info-card { background:#0a0d16; border:1px solid #1a2030; border-radius:10px; padding:14px 18px; }
        .info-card p { margin:0; font-size:12.5px; color:#8b93a8; line-height:1.65; }
        .info-card strong { color:#dde3ee; }
        .info-card .num { font-family:'SFMono-Regular','Menlo','Consolas',monospace; color:#dde3ee; background:#13182a; padding:2px 8px; border-radius:5px; font-size:12px; }

        .code-block { padding:22px 40px 8px; }
        .code-frame { background:#0a0d16; border:1px solid #2c3148; border-radius:14px; padding:22px 16px; text-align:center; position:relative; }
        .code-eyebrow { font-family:'SFMono-Regular','Menlo','Consolas',monospace; font-size:10px; font-weight:600; letter-spacing:2px; color:#6c7488; text-transform:uppercase; margin:0 0 10px; }
        .email-code { display:inline-block; font-family:'SFMono-Regular','Menlo','Consolas',monospace; font-size:34px; font-weight:600; letter-spacing:9px; color:#f0f2f7; line-height:1; padding-left:9px; }

        .meta-row { padding:18px 40px 22px; }
        .meta-row .item { font-family:'SFMono-Regular','Menlo','Consolas',monospace; font-size:10.5px; letter-spacing:1.2px; color:#5c6577; text-transform:uppercase; }
        .meta-row .item strong { color:#9aa1b3; font-weight:600; }

        .footer-card { padding:22px 4px 0; }
        .footer-card p { margin:0 0 6px; font-size:11.5px; line-height:1.6; color:#54596a; }
        .footer-card p:last-child { margin-bottom:0; font-size:10.5px; }
        .footer-card a { color:#7c83a0; }
    </style>
    <#if theme == "LIGHT">
    <style>
        body { background:#f4f5f9 !important; color:#171a26 !important; }
        .wrapper { background:#f4f5f9 !important; }
        .ticker-cell { color:#7c83a0 !important; }
        .ticker-divider { color:#cdd1de !important; }
        .card { background:#ffffff !important; border-color:#e7e9f1 !important; box-shadow:0 4px 24px -8px rgba(20,22,40,0.08) !important; }
        .hero-title { color:#0d111e !important; }
        .hero-sub { color:#5c6479 !important; }
        .eyebrow { color:#5b5ef0 !important; }
        .body-section { color:#3d4458 !important; }
        .body-section strong { color:#0d111e !important; }
        .body-section a { color:#5b5ef0 !important; }
        .divider { background:#e7e9f1 !important; }
        .info-card { background:#f7f8fc !important; border-color:#e7e9f1 !important; }
        .info-card p { color:#5c6479 !important; }
        .info-card strong { color:#0d111e !important; }
        .info-card .num { color:#0d111e !important; background:#e7e9f1 !important; }
        .code-frame { background:#f7f8fc !important; border-color:#dee0eb !important; }
        .code-eyebrow { color:#7c83a0 !important; }
        .email-code { color:#0d111e !important; }
        .meta-row .item { color:#7c83a0 !important; }
        .meta-row .item strong { color:#3d4458 !important; }
        .footer-card p { color:#7c83a0 !important; }
        .footer-card a { color:#5b5ef0 !important; }
    </style>
    </#if>
</head>
<body>
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" class="wrapper">
    <tr>
        <td align="center">
            <table role="presentation" class="container" width="600" cellspacing="0" cellpadding="0" border="0" style="width:600px;max-width:600px;">
                <tr>
                    <td align="left" class="ticker-row">
                        <span class="ticker-mark"></span><span class="ticker-cell"><strong style="color:#9aa1b3;font-weight:700;letter-spacing:1.6px;">FINANCE</strong> <span class="ticker-divider">·</span> PORTAL <span class="ticker-divider">·</span> ${.now?string("dd.MM.yyyy")}</span>
                    </td>
                </tr>
            </table>

            <table role="presentation" class="container card" width="600" cellspacing="0" cellpadding="0" border="0">
                <tr><td class="accent-strip accent-strip-grad" style="height:2px;line-height:2px;font-size:0;background:#6366f1;background-image:linear-gradient(90deg,#6366f1 0%,#8b5cf6 50%,#a78bfa 100%);">&nbsp;</td></tr>
                <tr>
                    <td class="hero hero-pad">
                        <#if eyebrow?has_content><p class="eyebrow">${eyebrow}</p></#if>
                        <#if title?has_content><h1 class="hero-title">${title}</h1></#if>
                        <#if subtitle?has_content><p class="hero-sub">${subtitle}</p></#if>
                    </td>
                </tr>
                <tr>
                    <td class="body-section body-pad">
                        <#nested>
                    </td>
                </tr>
                <tr><td><div class="divider">&nbsp;</div></td></tr>
                <tr>
                    <td class="meta-row">
                        <span class="item"><strong>REF</strong> &nbsp; ${(user.id!"-")?substring(0, 8)}</span>
                        <span class="ticker-divider">&nbsp;·&nbsp;</span>
                        <span class="item"><strong>SENT</strong> &nbsp; ${.now?string("HH:mm")} TRT</span>
                    </td>
                </tr>
            </table>

            <table role="presentation" class="container" width="600" cellspacing="0" cellpadding="0" border="0" style="width:600px;max-width:600px;">
                <tr>
                    <td class="footer-card">
                        <p>Bu e-posta Finance Portal hesabına bağlı bir işlem nedeniyle otomatik olarak gönderilmiştir.</p>
                        <p>İçerik tanıdık değilse e-postayı yok sayabilirsin — başka bir aksiyon gerekmez.</p>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
</table>
</body>
</html>
</#macro>
