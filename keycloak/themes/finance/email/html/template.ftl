<#macro emailLayout>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <title>Finance Portal</title>
    <style>
        @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap');

        body {
            margin: 0;
            padding: 0;
            background-color: #0e0e14;
            font-family: 'Plus Jakarta Sans', system-ui, -apple-system, sans-serif;
            color: #ededf0;
            -webkit-font-smoothing: antialiased;
            -moz-osx-font-smoothing: grayscale;
        }

        .email-wrapper {
            width: 100%;
            background-color: #0e0e14;
            padding: 40px 0;
        }

        .email-container {
            max-width: 560px;
            margin: 0 auto;
            background: rgba(22, 22, 30, 0.85);
            border: 1px solid rgba(99, 102, 241, 0.1);
            border-radius: 16px;
            overflow: hidden;
        }

        .email-header {
            padding: 28px 28px 20px;
            border-bottom: 1px solid rgba(99, 102, 241, 0.1);
            text-align: left;
        }

        .email-header h1 {
            font-size: 22px;
            font-weight: 700;
            color: #ededf0;
            margin: 0;
            letter-spacing: -0.02em;
        }

        .email-header p {
            font-size: 14px;
            color: #8b8b9a;
            margin: 6px 0 0 0;
        }

        .email-body {
            padding: 28px;
        }

        .email-body p {
            font-size: 14px;
            color: #8b8b9a;
            line-height: 1.7;
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
            padding: 12px 28px;
            font-size: 14px;
            font-weight: 600;
            font-family: 'Plus Jakarta Sans', system-ui, sans-serif;
            color: #ffffff !important;
            background: linear-gradient(135deg, #6366f1, #818cf8);
            border-radius: 12px;
            text-decoration: none;
            margin: 8px 0 16px 0;
            box-shadow: 0 4px 20px rgba(99,102,241,0.35);
        }

        .email-info-box {
            padding: 16px;
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
            padding: 16px 28px;
            border-top: 1px solid rgba(99, 102, 241, 0.1);
        }

        .email-footer p {
            font-size: 12px;
            color: #55555f;
            margin: 0;
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
                <h1>Finance Portal</h1>
                <p>Secure Authentication</p>
            </div>
            <div class="email-body">
                <#nested>
            </div>
            <div class="email-footer">
                <p>Bu e-posta Finance Portal tarafindan otomatik olarak gonderilmistir.</p>
            </div>
        </div>
    </div>
</body>
</html>
</#macro>
