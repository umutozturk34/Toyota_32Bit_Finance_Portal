<#import "template.ftl" as layout>
<@layout.emailLayout title="Hesap işlemi onayı" subtitle="Tamamlanması gereken bir adım var">
    <p>Merhaba <strong>${user.firstName!''}</strong>,</p>
    <p>Hesabın için tamamlamanı bekleyen bir işlem var. Aşağıdaki butona tıklayarak güvenli sayfada işlemi tamamlayabilirsin.</p>
    <div class="cta-wrap">
        <a href="${link}" class="email-cta">İşlemi Tamamla</a>
    </div>
    <div class="email-info-box">
        <p>Bu bağlantı ${linkExpiration} dakika içinde geçerliliğini yitirir.</p>
    </div>
</@layout.emailLayout>
