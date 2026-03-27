<#import "template.ftl" as layout>
<@layout.emailLayout>
    <p>Merhaba <strong>${user.firstName!""}</strong>,</p>
    <p>Şifrenizi sıfırlama talebinde bulundunuz. Aşağıdaki butona tıklayarak yeni bir şifre belirleyebilirsiniz.</p>
    <p>
        <a href="${link}" class="email-cta">Şifremi Sıfırla</a>
    </p>
    <div class="email-info-box">
        <p>Bu bağlantı <strong>${linkExpiration}</strong> dakika içinde geçerliliğini yitirecektir.</p>
    </div>
    <p>Eğer bu işlemi siz başlatmadıysanız hesabınız güvendedir ve bu e-postayı görmezden gelebilirsiniz.</p>
</@layout.emailLayout>
