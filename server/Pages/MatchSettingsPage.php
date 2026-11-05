<?php
namespace Kripusek\CS2MatchControl\Filament\Server\Pages;
use App\Enums\ContainerStatus;use Filament\Actions\Action;use Filament\Facades\Filament;
use Filament\Forms\Components\Select;use Filament\Forms\Concerns\InteractsWithForms;
use Filament\Pages\Concerns\InteractsWithFormActions;use Filament\Notifications\Notification;
use Filament\Pages\Page;use Filament\Schemas\Components\Section;use Livewire\Attributes\Locked;
use Kripusek\CS2MatchControl\Services\{ServerAccess,MatchSettingsService,WorkshopService,MapCompatibility};
class MatchSettingsPage extends Page {
 use InteractsWithForms;use InteractsWithFormActions;
 protected static string|\BackedEnum|null $navigationIcon='heroicon-o-adjustments-horizontal';
 protected static ?string $slug='cs2-match-settings';protected static ?int $navigationSort=23;
 protected string $view='cs2-match-control::filament.server.pages.match-settings';
 public array $formData=[];#[Locked]public array $mapOptions=[];#[Locked]public array $workshopOptions=[];#[Locked]public bool $ready=false;
 #[Locked]public array $workshopItems=[];
 public static function canAccess(): bool{return parent::canAccess()&&ServerAccess::allowed(true);}
 public static function getNavigationLabel(): string{return 'Ustawienia meczu';}
 public static function getNavigationGroup(): ?string{return 'Counter-Strike 2';}
 public function getTitle(): string{return 'Ustawienia meczu';}
 public function mount(MatchSettingsService $settings,WorkshopService $workshop): void {
  ServerAccess::require(true);
  try{$s=Filament::getTenant();$this->mapOptions=$settings->nativeMaps($s);$this->workshopItems=$workshop->library($s);foreach($this->workshopItems as $id=>$item)$this->workshopOptions[$id]=$item['title'].' ('.$id.')';$this->form->fill($this->compatibleState($settings->read($s)));$this->ready=true;}
  catch(\Throwable $e){report($e);Notification::make()->title('Nie udało się odczytać ustawień')->body('Sprawdź połączenie z Wings i uprawnienia do plików.')->danger()->send();}
 }
 public function compatibleState(array $state): array {
  $preset=(string)($state['preset']??'competitive');$native=MapCompatibility::nativeMaps($this->mapOptions,$preset);$workshop=$this->compatibleWorkshopOptions($preset);
  if(!isset($native[$state['nativeMap']??'']))$state['nativeMap']=array_key_first($native)??'';
  if(!isset($workshop[$state['workshopMap']??'']))$state['workshopMap']=array_key_first($workshop)??'';
  return $state;
 }
 public function compatibleWorkshopOptions(string $preset): array {
  return array_intersect_key($this->workshopOptions,array_filter($this->workshopItems,fn($item)=>MapCompatibility::workshopCompatible($item,$preset)));
 }
 public function getFormSchema(): array {
  $modes=[];foreach(MatchSettingsService::MODES as $key=>$mode)$modes[$key]=$mode[0];
  return [Section::make('Mapa i tryb')->columns(2)->schema([
   Select::make('preset')->label('Tryb gry')->options($modes)->required()->native(false)->live()->afterStateUpdated(function($state,$get,$set){$normalized=$this->compatibleState(['preset'=>$state,'nativeMap'=>$get('nativeMap'),'workshopMap'=>$get('workshopMap')]);$set('nativeMap',$normalized['nativeMap']);$set('workshopMap',$normalized['workshopMap']);}),
   Select::make('source')->label('Źródło mapy')->options(['native'=>'Mapa zainstalowana z CS2','workshop'=>'Mapa z biblioteki Workshop'])->live()->required(),
   Select::make('nativeMap')->label('Mapa CS2 / mapa zapasowa')->options(fn($get)=>MapCompatibility::nativeMaps($this->mapOptions,(string)$get('preset')))->searchable()->required()->helperText('Wingman pokazuje tylko mapy obsługujące ten tryb. Przy mapie Workshop ta mapa jest zapasowa.'),
   Select::make('workshopMap')->label('Mapa Workshop')->options(fn($get)=>$this->compatibleWorkshopOptions((string)$get('preset')))->searchable()->visible(fn($get)=>$get('source')==='workshop')->helperText('Dla Wingmana wymagane jest oznaczenie Wingman w Steam Workshop.'),
  ])];
 }
 protected function getFormStatePath(): ?string{return 'formData';}
 protected function getHeaderActions(): array{return [
  Action::make('save')->label('Zapisz na następny start')->icon('heroicon-o-check')->disabled(fn()=>!$this->ready)->action('saveSettings'),
  Action::make('apply')->label('Zastosuj i zmień mapę')->icon('heroicon-o-play')->color('warning')->disabled(fn()=>!$this->ready)->requiresConfirmation()->modalHeading('Zastosować ustawienia teraz?')->modalDescription('Zakończy to bieżący mecz i załaduje wybraną mapę. Ustawienia zostaną też zapisane na kolejne uruchomienie serwera.')->action('applyNow'),
 ];}
 public function saveSettings(): void {
  ServerAccess::require(true);abort_unless($this->ready,409);
  try{app(MatchSettingsService::class)->save(Filament::getTenant(),$this->form->getState());Notification::make()->title('Zapisano ustawienia startowe')->body('Bieżący mecz trwa dalej. Nowe ustawienia wejdą przy następnym starcie serwera.')->success()->send();}
  catch(\Illuminate\Validation\ValidationException $e){throw $e;}catch(\Throwable $e){report($e);Notification::make()->title('Nie udało się zapisać ustawień')->body('Sprawdź konfigurację startową i połączenie z Wings.')->danger()->send();}
 }
 public function applyNow(): void {
  ServerAccess::require(true);abort_unless($this->ready,409);$s=Filament::getTenant();
  try{
   if($s->retrieveStatus()!==ContainerStatus::Running)throw new \RuntimeException('Uruchom serwer przed zmianę mapy.');
   $service=app(MatchSettingsService::class);$d=$service->save($s,$this->form->getState());$r=$s->send($service->commands($s,$d));
   if($r->getStatusCode()>=300)throw new \RuntimeException('Ustawienia zapisano, ale nie udało się wysłać komend.');
   Notification::make()->title('Zapisano i zlecono zmianę mapy')->body('Postęp ładowania lub pobierania mapy sprawdzisz w konsoli serwera.')->success()->send();
  }catch(\Illuminate\Validation\ValidationException $e){throw $e;}catch(\Throwable $e){report($e);Notification::make()->title('Nie udało się zastosować ustawień')->body('Sprawdź, czy serwer działa i jest połączony z Wings.')->danger()->send();}
 }
}
