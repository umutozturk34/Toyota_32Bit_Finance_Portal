<#macro emailLayout>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <title>Finance Portal</title>
    <style>
        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');

        body {
            margin: 0;
            padding: 0;
            background-color: #050506;
            font-family: 'Inter', system-ui, -apple-system, BlinkMacSystemFont, sans-serif;
            color: #EDEDEF;
            -webkit-font-smoothing: antialiased;
            -moz-osx-font-smoothing: grayscale;
        }

        .email-wrapper {
            width: 100%;
            background-color: #050506;
            padding: 40px 0;
        }

        .email-container {
            max-width: 560px;
            margin: 0 auto;
            background: rgba(255, 255, 255, 0.03);
            border: 1px solid rgba(140, 130, 220, 0.12);
            border-radius: 12px;
            overflow: hidden;
        }

        .email-header {
            padding: 24px;
            border-bottom: 1px solid rgba(140, 130, 220, 0.12);
            text-align: left;
        }

        .email-header h1 {
            font-size: 20px;
            font-weight: 700;
            color: #EDEDEF;
            margin: 0;
            letter-spacing: -0.02em;
        }

        .email-header p {
            font-size: 14px;
            color: #8A8F98;
            margin: 4px 0 0 0;
        }

        .email-body {
            padding: 24px;
        }

        .email-body p {
            font-size: 14px;
            color: #8A8F98;
            line-height: 1.6;
            margin: 0 0 16px 0;
        }

        .email-body p:last-child {
            margin-bottom: 0;
        }

        .email-body strong {
            color: #EDEDEF;
            font-weight: 600;
        }

        .email-cta {
            display: inline-block;
            padding: 10px 24px;
            font-size: 14px;
            font-weight: 600;
            font-family: 'Inter', system-ui, sans-serif;
            color: #ffffff !important;
            background-color: #5E6AD2;
            border-radius: 8px;
            text-decoration: none;
            margin: 8px 0 16px 0;
        }

        .email-info-box {
            padding: 16px;
            background: rgba(255, 255, 255, 0.05);
            border: 1px solid rgba(140, 130, 220, 0.12);
            border-radius: 8px;
            margin: 16px 0;
        }

        .email-info-box p {
            font-size: 13px;
            color: #8A8F98;
            margin: 0;
        }

        .email-info-box code {
            font-size: 13px;
            color: #EDEDEF;
            background: rgba(255, 255, 255, 0.08);
            padding: 2px 6px;
            border-radius: 4px;
        }

        .email-footer {
            padding: 16px 24px;
            border-top: 1px solid rgba(140, 130, 220, 0.12);
        }

        .email-footer p {
            font-size: 12px;
            color: rgba(255, 255, 255, 0.40);
            margin: 0;
        }

        .email-footer a {
            color: #5E6AD2;
            text-decoration: none;
        }

        a {
            color: #5E6AD2;
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
                <p>Bu e-posta Finance Portal tarafından otomatik olarak gönderilmiştir.</p>
            </div>
        </div>
    </div>
</body>
</html>
</#macro>
