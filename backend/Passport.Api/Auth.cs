using System.Security.Claims;
using System.Text.Encodings.Web;
using FirebaseAdmin;
using FirebaseAdmin.Auth;
using Google.Apis.Auth.OAuth2;
using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Options;

namespace Passport.Api;
public interface ITokenVerifier { Task<ClaimsPrincipal> Verify(string token, CancellationToken cancellation); }
public sealed class FirebaseTokenVerifier(IConfiguration configuration) : ITokenVerifier
{
    private readonly Lazy<FirebaseAuth> auth = new(() => FirebaseAuth.GetAuth(FirebaseApp.Create(new AppOptions {
        ProjectId = configuration["Firebase:ProjectId"] ?? throw new InvalidOperationException("Configure Firebase:ProjectId."),
        Credential = GoogleCredential.GetApplicationDefault()
    })));
    public async Task<ClaimsPrincipal> Verify(string token, CancellationToken cancellation)
    {
        var decoded = await auth.Value.VerifyIdTokenAsync(token, true, cancellation);
        var claims = new List<Claim> { new(ClaimTypes.NameIdentifier, decoded.Uid) };
        if (decoded.Claims.TryGetValue("email", out var email)) claims.Add(new(ClaimTypes.Email, email.ToString()!));
        return new ClaimsPrincipal(new ClaimsIdentity(claims, "Firebase"));
    }
}
public sealed class FirebaseHandler(IOptionsMonitor<AuthenticationSchemeOptions> options, ILoggerFactory logger,
    UrlEncoder encoder, ITokenVerifier verifier) : AuthenticationHandler<AuthenticationSchemeOptions>(options, logger, encoder)
{
    protected override async Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        var header = Request.Headers.Authorization.ToString();
        if (!header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase)) return AuthenticateResult.NoResult();
        var token = header[7..].Trim();
        if (token.Length is 0 or > 16384) return AuthenticateResult.Fail("Invalid token.");
        try { return AuthenticateResult.Success(new AuthenticationTicket(await verifier.Verify(token, Context.RequestAborted), Scheme.Name)); }
        catch (FirebaseAuthException) { return AuthenticateResult.Fail("Your session expired. Please sign in again."); }
        catch (ArgumentException) { return AuthenticateResult.Fail("Invalid token."); }
    }
}
