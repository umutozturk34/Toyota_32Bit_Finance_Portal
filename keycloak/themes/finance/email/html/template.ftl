<#macro emailLayout>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <title>Finance Portal</title>
    <style>
        @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700&display=swap');

        body {
            margin: 0;
            padding: 0;
            background-color: #08080c;
            font-family: 'Plus Jakarta Sans', system-ui, -apple-system, sans-serif;
            color: #ededf0;
            -webkit-font-smoothing: antialiased;
            -moz-osx-font-smoothing: grayscale;
        }

        .email-wrapper {
            width: 100%;
            background: linear-gradient(180deg, #08080c 0%, #0e0e14 50%, #08080c 100%);
            padding: 48px 0;
        }

        .email-container {
            max-width: 520px;
            margin: 0 auto;
            background: rgba(22, 22, 30, 0.9);
            border: 1px solid rgba(99, 102, 241, 0.12);
            border-radius: 20px;
            overflow: hidden;
            box-shadow: 0 0 80px rgba(99, 102, 241, 0.06), 0 0 32px rgba(99, 102, 241, 0.04);
        }

        .email-header {
            padding: 32px 32px 24px;
            border-bottom: 1px solid rgba(99, 102, 241, 0.1);
            background: linear-gradient(180deg, rgba(99, 102, 241, 0.04) 0%, transparent 100%);
        }

        .email-logo {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .email-logo-icon {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 40px;
            height: 40px;
            border-radius: 12px;
            background: linear-gradient(135deg, #6366f1, #a855f7, #14b8a6);
            box-shadow: 0 6px 20px rgba(99, 102, 241, 0.25);
        }

        .email-logo-text {
            font-size: 20px;
            font-weight: 800;
            color: #ededf0;
            letter-spacing: -0.03em;
        }

        .email-header-sub {
            font-size: 13px;
            color: #55555f;
            margin: 8px 0 0 52px;
        }

        .email-body {
            padding: 32px;
        }

        .email-body p {
            font-size: 14px;
            color: #8b8b9a;
            line-height: 1.75;
            margin: 0 0 16px 0;
        }

        .email-body p:last-child {
            margin-bottom: 0;
        }

        .email-body strong {
            color: #ededf0;
            font-weight: 600;
        }

        .email-cta {
            display: inline-block;
            padding: 14px 32px;
            font-size: 14px;
            font-weight: 600;
            font-family: 'Plus Jakarta Sans', system-ui, sans-serif;
            color: #ffffff !important;
            background: linear-gradient(135deg, #6366f1, #818cf8);
            border-radius: 12px;
            text-decoration: none;
            margin: 8px 0 16px 0;
            box-shadow: 0 4px 24px rgba(99, 102, 241, 0.4), inset 0 1px 0 rgba(255,255,255,0.1);
        }

        .email-info-box {
            padding: 16px 20px;
            background: rgba(99, 102, 241, 0.06);
            border: 1px solid rgba(99, 102, 241, 0.1);
            border-radius: 12px;
            margin: 16px 0;
        }

        .email-info-box p {
            font-size: 13px;
            color: #8b8b9a;
            margin: 0;
        }

        .email-info-box code {
            font-size: 13px;
            font-family: 'JetBrains Mono', monospace;
            color: #ededf0;
            background: rgba(99, 102, 241, 0.1);
            padding: 2px 8px;
            border-radius: 6px;
        }

        .email-footer {
            padding: 20px 32px;
            border-top: 1px solid rgba(99, 102, 241, 0.08);
            background: rgba(8, 8, 12, 0.3);
        }

        .email-footer p {
            font-size: 11px;
            color: #55555f;
            margin: 0;
            line-height: 1.6;
        }

        .email-footer a {
            color: #6366f1;
            text-decoration: none;
        }

        a {
            color: #6366f1;
            text-decoration: none;
        }
    </style>
</head>
<body>
    <div class="email-wrapper">
        <div class="email-container">
            <div class="email-header">
                <div class="email-logo">
                    <div class="email-logo-icon">
                        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#ffffff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/></svg>
                    </div>
                    <span class="email-logo-text">Finance Portal</span>
                </div>
                <p class="email-header-sub">Secure Authentication</p>
            </div>
            <div class="email-body">
                <#nested>
            </div>
            <div class="email-footer">
                <p>Bu e-posta Finance Portal tarafindan otomatik olarak gonderilmistir. Lutfen yanit vermeyin.</p>
            </div>
        </div>
    </div>
</body>
</html>
</#macro>
