<#import "template.ftl" as layout>
<@layout.emailLayout title="Şifre sıfırlama" subtitle="Yeni şifre belirleme bağlantısı">
    <p>Merhaba <strong>${user.firstName!''}</strong>,</p>
    <p>Şifre sıfırlama talebinde bulundun. Aşağıdaki butona tıklayarak yeni şifreni belirleyebilirsin.</p>
    <div class="cta-wrap">
        <a href="${link}" class="email-cta">Şifremi Sıfırla</a>
    </div>
    <div class="email-info-box">
        <p>Bu bağlantı ${linkExpiration} dakika içinde geçerliliğini yitirir.</p>
    </div>
    <p>Eğer bu işlemi sen başlatmadıysan hesabın güvende, e-postayı yok sayabilirsin.</p>
</@layout.emailLayout>
