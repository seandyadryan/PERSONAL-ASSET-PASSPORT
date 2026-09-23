using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Security.Claims;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Infrastructure;
using Microsoft.EntityFrameworkCore.Storage.ValueConversion;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Passport.Api;

namespace Passport.Api.Tests;
public sealed class TestVerifier : ITokenVerifier
{
 public Task<ClaimsPrincipal> Verify(string token, CancellationToken cancellation)
 {
  if (token is not ("alice" or "bob")) throw new ArgumentException("Rejected test token");
  return Task.FromResult(new ClaimsPrincipal(new ClaimsIdentity([new Claim(ClaimTypes.NameIdentifier, token)], "Firebase")));
 }
}
public sealed class TestModelCustomizer(ModelCustomizerDependencies deps) : RelationalModelCustomizer(deps)
{
 public override void Customize(ModelBuilder builder, DbContext context)
 {
  base.Customize(builder, context);
  foreach (var entity in builder.Model.GetEntityTypes())
   foreach (var property in entity.GetProperties())
    if (property.ClrType == typeof(DateTimeOffset) || property.ClrType == typeof(DateTimeOffset?))
     property.SetValueConverter(new DateTimeOffsetToBinaryConverter());
 }
}
public sealed class ApiFactory : WebApplicationFactory<Program>
{
 private readonly SqliteConnection connection = new("DataSource=:memory:");
 protected override void ConfigureWebHost(IWebHostBuilder builder) => builder.ConfigureServices(services => {
  connection.Open();
  services.RemoveAll<DbContextOptions<PassportDb>>();
  services.RemoveAll<PassportDb>();

  services.AddDbContext<PassportDb>(o => o.UseSqlite(connection).ReplaceService<IModelCustomizer, TestModelCustomizer>());
  services.RemoveAll<ITokenVerifier>(); services.AddSingleton<ITokenVerifier, TestVerifier>();
 });
 public HttpClient Client(string? user = "alice")
 {
  var client = CreateClient();
  using var scope = Services.CreateScope(); scope.ServiceProvider.GetRequiredService<PassportDb>().Database.EnsureCreated();
  if (user != null) client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", user);
  return client;
 }
 protected override void Dispose(bool disposing) { base.Dispose(disposing); if (disposing) connection.Dispose(); }
}
public class ApiTests
{
 [Fact] public async Task Authentication_is_required_and_bad_tokens_are_rejected()
 {
  using var app = new ApiFactory();
  Assert.Equal(HttpStatusCode.Unauthorized, (await app.Client(null).GetAsync("/api/assets")).StatusCode);
  Assert.Equal(HttpStatusCode.Unauthorized, (await app.Client("invalid").GetAsync("/api/me")).StatusCode);
  Assert.Equal(HttpStatusCode.OK, (await app.Client().GetAsync("/api/me")).StatusCode);
 }
 [Fact] public async Task Crud_enforces_ownership_versions_pagination_and_soft_delete()
 {
  using var app = new ApiFactory(); using var alice = app.Client(); using var bob = app.Client("bob");
  var input = new AssetInput("Camera", "Photography", "USD", 1200);
  var create = await alice.PostAsJsonAsync("/api/assets", input); Assert.Equal(HttpStatusCode.Created, create.StatusCode);
  var asset = (await create.Content.ReadFromJsonAsync<AssetResponse>())!;
  Assert.Equal(HttpStatusCode.NotFound, (await bob.GetAsync($"/api/assets/{asset.Id}")).StatusCode);
  Assert.Equal(HttpStatusCode.NotFound, (await bob.PutAsJsonAsync($"/api/assets/{asset.Id}", input)).StatusCode);
  Assert.Equal(HttpStatusCode.NotFound, (await bob.DeleteAsync($"/api/assets/{asset.Id}?version=1")).StatusCode);
  Assert.Equal(HttpStatusCode.OK, (await alice.GetAsync("/api/assets?page=1&pageSize=1")).StatusCode);
  Assert.Equal(HttpStatusCode.BadRequest, (await alice.GetAsync("/api/assets?pageSize=101")).StatusCode);
  Assert.Equal(HttpStatusCode.OK, (await alice.PutAsJsonAsync($"/api/assets/{asset.Id}", input with { Name = "Updated" })).StatusCode);
  Assert.Equal(HttpStatusCode.Conflict, (await alice.PutAsJsonAsync($"/api/assets/{asset.Id}", input)).StatusCode);
  Assert.Equal(HttpStatusCode.Conflict, (await alice.DeleteAsync($"/api/assets/{asset.Id}?version=1")).StatusCode);
  Assert.Equal(HttpStatusCode.NoContent, (await alice.DeleteAsync($"/api/assets/{asset.Id}?version=2")).StatusCode);
  Assert.Equal(HttpStatusCode.NotFound, (await alice.GetAsync($"/api/assets/{asset.Id}")).StatusCode);
 }
 [Fact] public async Task Free_limit_is_enforced_by_server()
 {
  using var app = new ApiFactory(); using var client = app.Client();
  for (var i = 0; i < 10; i++) Assert.Equal(HttpStatusCode.Created, (await client.PostAsJsonAsync("/api/assets", new AssetInput($"Asset {i}", "Other", "IDR", 100))).StatusCode);
  Assert.Equal(HttpStatusCode.Conflict, (await client.PostAsJsonAsync("/api/assets", new AssetInput("Extra", "Other", "IDR", 100))).StatusCode);
 }
 [Fact] public void Validation_rejects_bad_dates_money_and_empty_names()
 {
  Assert.NotEmpty(new AssetInput("", "Other", "ZZZ", -1).Validate());
  Assert.NotEmpty(new AssetInput("Laptop", "Other", "USD", 1.111m).Validate());
  Assert.NotEmpty(new AssetInput("Laptop", "Other", "USD", 1, PurchaseDate: new(2026,9,17), WarrantyEnd: new(2026,9,16)).Validate());
  Assert.Empty(new AssetInput("Laptop", "Other", "USD", 1, PurchaseDate: new(2026,9,17), ReturnEnd: new(2026,9,17)).Validate());
 }
}

