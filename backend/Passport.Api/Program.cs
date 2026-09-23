using System.Security.Claims;
using System.Threading.RateLimiting;
using Microsoft.AspNetCore.Authentication;
using Microsoft.EntityFrameworkCore;
using Passport.Api;

var builder = WebApplication.CreateBuilder(args);
builder.Services.AddProblemDetails();
builder.Services.AddDbContext<PassportDb>(o => o.UseNpgsql(builder.Configuration.GetConnectionString("Database")));
builder.Services.AddSingleton<ITokenVerifier, FirebaseTokenVerifier>();
builder.Services.AddAuthentication("Firebase").AddScheme<AuthenticationSchemeOptions, FirebaseHandler>("Firebase", _ => { });
builder.Services.AddAuthorization();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();
builder.Services.AddRateLimiter(o => {
    o.RejectionStatusCode = 429;
    o.AddPolicy("api", context => RateLimitPartition.GetFixedWindowLimiter(
        context.Connection.RemoteIpAddress?.ToString() ?? "unknown",
        _ => new FixedWindowRateLimiterOptions { PermitLimit = 120, Window = TimeSpan.FromMinutes(1), QueueLimit = 0 }));
});
builder.WebHost.ConfigureKestrel(o => o.Limits.MaxRequestBodySize = 64 * 1024);
var app = builder.Build();
app.UseExceptionHandler();
if (!app.Environment.IsDevelopment()) app.UseHsts();
if (app.Environment.IsDevelopment()) { app.UseSwagger(); app.UseSwaggerUI(); }
app.UseRateLimiter();
app.UseAuthentication();
app.UseAuthorization();
app.MapGet("/health", () => Results.Ok(new { status = "ok" }));
var api = app.MapGroup("/api").RequireAuthorization().RequireRateLimiting("api");
api.MapGet("/me", (ClaimsPrincipal user) => Results.Ok(new {
    uid = user.FindFirstValue(ClaimTypes.NameIdentifier), email = user.FindFirstValue(ClaimTypes.Email),
    entitlement = "FREE", cloudSyncEnabled = false
}));
api.MapGet("/assets", async (ClaimsPrincipal user, PassportDb db, int page = 1, int pageSize = 25, string? search = null) => {
    if (page < 1 || page > 100000 || pageSize is < 1 or > 100 || search?.Length > 200) return Results.BadRequest();
    var query = db.Assets.AsNoTracking().Where(x => x.OwnerUid == Identity.Uid(user) && x.DeletedAt == null);
    if (!string.IsNullOrWhiteSpace(search)) query = query.Where(x => x.Name.Contains(search) || x.Brand.Contains(search) || x.Serial.Contains(search));
    return Results.Ok(new { page, pageSize, total = await query.CountAsync(), items = await query.OrderByDescending(x => x.CreatedAt).ThenBy(x => x.Id).Skip((page - 1) * pageSize).Take(pageSize).Select(x => AssetResponse.From(x)).ToListAsync() });
});
api.MapGet("/assets/{id:guid}", async (Guid id, ClaimsPrincipal user, PassportDb db) => {
    var asset = await db.Assets.AsNoTracking().SingleOrDefaultAsync(x => x.Id == id && x.OwnerUid == Identity.Uid(user) && x.DeletedAt == null);
    return asset == null ? Results.NotFound() : Results.Ok(AssetResponse.From(asset));
});
api.MapPost("/assets", async (AssetInput input, ClaimsPrincipal user, PassportDb db, ILogger<Program> log) => {
    var errors = input.Validate();
    if (errors.Count != 0) return Results.ValidationProblem(errors);
    await using var tx = await db.Database.BeginTransactionAsync(System.Data.IsolationLevel.Serializable);
    if (await db.Assets.CountAsync(x => x.OwnerUid == Identity.Uid(user) && x.DeletedAt == null) >= 10)
        return Results.Problem("The free plan supports up to 10 assets.", statusCode: 409);
    var asset = new Asset { OwnerUid = Identity.Uid(user) }; input.Apply(asset);
    db.Assets.Add(asset); db.Events.Add(new AssetEvent { AssetId = asset.Id, Kind = "created" });
    await db.SaveChangesAsync(); await tx.CommitAsync();
    log.LogInformation("Asset created {AssetId}", asset.Id);
    return Results.Created($"/api/assets/{asset.Id}", AssetResponse.From(asset));
});
api.MapPut("/assets/{id:guid}", async (Guid id, AssetInput input, ClaimsPrincipal user, PassportDb db) => {
    var errors = input.Validate(); if (errors.Count != 0) return Results.ValidationProblem(errors);
    var asset = await db.Assets.SingleOrDefaultAsync(x => x.Id == id && x.OwnerUid == Identity.Uid(user) && x.DeletedAt == null);
    if (asset == null) return Results.NotFound();
    if (input.Version != asset.Version) return Results.Conflict(new { message = "This asset changed. Refresh before saving." });
    input.Apply(asset); asset.Version++; asset.UpdatedAt = DateTimeOffset.UtcNow;
    db.Events.Add(new AssetEvent { AssetId = id, Kind = "updated" });
    try { await db.SaveChangesAsync(); } catch (DbUpdateConcurrencyException) { return Results.Conflict(); }
    return Results.Ok(AssetResponse.From(asset));
});
api.MapDelete("/assets/{id:guid}", async (Guid id, int version, ClaimsPrincipal user, PassportDb db, ILogger<Program> log) => {
    var asset = await db.Assets.SingleOrDefaultAsync(x => x.Id == id && x.OwnerUid == Identity.Uid(user) && x.DeletedAt == null);
    if (asset == null) return Results.NotFound();
    if (version != asset.Version) return Results.Conflict();
    asset.DeletedAt = asset.UpdatedAt = DateTimeOffset.UtcNow; asset.Version++;
    db.Events.Add(new AssetEvent { AssetId = id, Kind = "deleted" });
    try { await db.SaveChangesAsync(); } catch (DbUpdateConcurrencyException) { return Results.Conflict(); }
    log.LogInformation("Asset deleted {AssetId}", id); return Results.NoContent();
});
api.MapGet("/dashboard", async (ClaimsPrincipal user, PassportDb db) => {
    var assets = await db.Assets.AsNoTracking().Where(x => x.OwnerUid == Identity.Uid(user) && x.DeletedAt == null).ToListAsync();
    var today = DateOnly.FromDateTime(DateTime.UtcNow);
    return Results.Ok(new { count = assets.Count, warrantyExpiring = assets.Count(x => x.WarrantyEnd >= today && x.WarrantyEnd <= today.AddDays(30)), returnsExpiring = assets.Count(x => x.ReturnEnd >= today && x.ReturnEnd <= today.AddDays(7)), purchaseTotals = assets.GroupBy(x => x.Currency).ToDictionary(x => x.Key, x => x.Sum(a => a.Price)) });
});
app.Run();
public static class Identity { public static string Uid(ClaimsPrincipal user) => user.FindFirstValue(ClaimTypes.NameIdentifier)!; }
public partial class Program;

