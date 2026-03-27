<#import "template.ftl" as layout>
<@layout.emailLayout>
    <p>Merhaba <strong>${user.firstName!""}</strong>,</p>
    <p>Hesabınızla ilişkili e-posta adresini doğrulamanız gerekmektedir.</p>
    <p>
        <a href="${link}" class="email-cta">E-postamı Doğrula</a>
    </p>
    <div class="email-info-box">
        <p>Bu bağlantı <strong>${linkExpiration}</strong> dakika içinde geçerliliğini yitirecektir.</p>
    </div>
    <p>Eğer bu işlemi siz başlatmadıysanız bu e-postayı güvenle görmezden gelebilirsiniz.</p>
</@layout.emailLayout>
