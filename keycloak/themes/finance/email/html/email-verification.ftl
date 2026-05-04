<#import "template.ftl" as layout>
<@layout.emailLayout>
    <p>Merhaba <strong>${user.firstName!""}</strong>,</p>
    <p>Hesabinizi dogrulamak icin asagidaki 6 haneli kodu kullanin:</p>
    <div style="text-align: center; margin: 28px 0;">
        <div class="email-code-box">
            <span class="email-code">${code}</span>
        </div>
    </div>
    <div class="email-info-box">
        <p><strong>Gecerlilik:</strong> Bu kod <strong>${linkExpiration}</strong> dakika icerisinde sona erecektir.</p>
    </div>
    <p style="font-size: 13px; color: #55555f;">Eger bu islemi siz baslatmadiyseniz bu e-postayi guvenle yok sayabilirsiniz.</p>
</@layout.emailLayout>
