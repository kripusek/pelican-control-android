<?php
namespace Kripusek\CS2MatchControl\Providers;

use Illuminate\Support\ServiceProvider;
use Illuminate\Support\Facades\Route;
use App\Http\Middleware\Api\Client\Server\AuthenticateServerAccess;
use Kripusek\CS2MatchControl\Http\MobileCs2Controller;
use Kripusek\CS2MatchControl\Http\MobileAdminController;

class MobileApiProvider extends ServiceProvider
{
 public function boot(): void {
  if ($this->app->routesAreCached()) return;
  Route::middleware(['api','client-api','throttle:api.client',AuthenticateServerAccess::class])
   ->prefix('api/client/servers/{server:uuid}/mobile-cs2')->scopeBindings()->group(function () {
    Route::get('/',[MobileCs2Controller::class,'show']);
    Route::post('/settings',[MobileCs2Controller::class,'settings']);
    Route::post('/workshop',[MobileCs2Controller::class,'workshop']);
    Route::post('/module/{module}',[MobileCs2Controller::class,'module']);
    Route::post('/action',[MobileCs2Controller::class,'action']);
   });
  Route::middleware(['api','application-api','throttle:api.application'])->prefix('api/application/mobile-control')->scopeBindings()->group(function () {
   Route::get('/permissions',[MobileAdminController::class,'permissions']);
   Route::post('/roles/{role:id}',[MobileAdminController::class,'role']);
   Route::get('/settings',[MobileAdminController::class,'settings']);
   Route::post('/settings',[MobileAdminController::class,'saveSettings']);
  });
 }
}
