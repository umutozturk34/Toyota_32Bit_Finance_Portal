<#import "template.ftl" as layout>
<@layout.emailLayout>
    <p>Merhaba <strong>${user.firstName!""}</strong>,</p>
    <p>Hesabinizi dogrulamak icin asagidaki 6 haneli kodu kullanin:</p>
    <div style="text-align: center; margin: 28px 0;">
        <div style="display: inline-block; padding: 20px 40px; background: linear-gradient(135deg, rgba(99, 102, 241, 0.12), rgba(168, 85, 247, 0.08)); border: 1px solid rgba(99, 102, 241, 0.2); border-radius: 16px; box-shadow: 0 8px 32px rgba(99, 102, 241, 0.15), inset 0 1px 0 rgba(255,255,255,0.05);">
            <span style="font-family: 'JetBrains Mono', monospace; font-size: 36px; font-weight: 700; letter-spacing: 10px; color: #ededf0; text-shadow: 0 0 20px rgba(99, 102, 241, 0.3);">${code}</span>
        </div>
    </div>
    <div class="email-info-box">
        <p><strong>Gecerlilik:</strong> Bu kod <strong>${linkExpiration}</strong> dakika icerisinde sona erecektir.</p>
    </div>
    <p style="font-size: 13px; color: #55555f;">Eger bu islemi siz baslatmadiyseniz bu e-postayi guvenle yok sayabilirsiniz.</p>
</@layout.emailLayout>
