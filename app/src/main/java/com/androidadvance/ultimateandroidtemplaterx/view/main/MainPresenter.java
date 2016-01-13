package com.androidadvance.ultimateandroidtemplaterx.view.main;

import com.androidadvance.ultimateandroidtemplaterx.BaseApplication;
import com.androidadvance.ultimateandroidtemplaterx.R;
import com.androidadvance.ultimateandroidtemplaterx.data.local.DbModel;
import com.androidadvance.ultimateandroidtemplaterx.data.local.DbModel_Table;
import com.androidadvance.ultimateandroidtemplaterx.data.remote.TheAPIService;
import com.androidadvance.ultimateandroidtemplaterx.events.MessagesEvent;
import com.androidadvance.ultimateandroidtemplaterx.model.weather.WeatherPojo;
import com.androidadvance.ultimateandroidtemplaterx.presenter.Presenter;
import com.google.gson.Gson;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import de.greenrobot.event.EventBus;
import retrofit.HttpException;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import timber.log.Timber;

public class MainPresenter implements Presenter<MainMvpView> {

  private MainMvpView mainMvpView;
  private Subscription subscription;
  private WeatherPojo weatherPojo;

  @Override public void attachView(MainMvpView view) {
    this.mainMvpView = view;
  }

  @Override public void detachView() {
    this.mainMvpView = null;
    if (subscription != null) subscription.unsubscribe();
  }

  public void loadWeather(String from_where) {

    load_from_db();

    String weather_from_where = from_where.trim();
    if (weather_from_where.isEmpty()) return;

    mainMvpView.showProgress();
    if (subscription != null) subscription.unsubscribe();

    BaseApplication baseApplication = BaseApplication.get(mainMvpView.getContext());
    TheAPIService apiService = baseApplication.getApiService();

    subscription = apiService.getWeatherForCity(weather_from_where, "metric")
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeOn(baseApplication.getSubscribeScheduler())
        .subscribe(new Subscriber<WeatherPojo>() {
          @Override public void onCompleted() {
            Timber.i("Weather loaded " + weatherPojo);
            mainMvpView.showWeather(weatherPojo);
            mainMvpView.hideProgress();

            store_in_db(weatherPojo);
          }

          @Override public void onError(Throwable error) {
            Timber.e(error, "Error loading weather");
            if (isHttp404(error)) {
              EventBus.getDefault().post(new MessagesEvent(false, baseApplication.getString(R.string.error_not_found)));
            } else {
              EventBus.getDefault().post(new MessagesEvent(false, baseApplication.getString(R.string.error_loading_weather)));
            }

            mainMvpView.hideProgress();
          }

          @Override public void onNext(WeatherPojo weatherPojo) {
            MainPresenter.this.weatherPojo = weatherPojo;
          }
        });
  }

  private void store_in_db(WeatherPojo weatherPojo) {

    Gson gson = new Gson();
    String serialized = gson.toJson(weatherPojo);
    long count = SQLite.selectCountOf(DbModel_Table.id).from(DbModel.class).count();

    if (count == 0) {
      DbModel dbModel = new DbModel(serialized, "", System.currentTimeMillis());
      dbModel.async().withListener(model -> Timber.d("Saved in the db")).save();
    } else {
      DbModel dbModel = new DbModel(serialized, "", System.currentTimeMillis());
      dbModel.setId(1);
      dbModel.async().withListener(model -> Timber.d("Updated")).update();
    }
  }

  private void load_from_db() {
    long count = SQLite.selectCountOf(DbModel_Table.id).from(DbModel.class).count();
    if (count > 0) {
      DbModel dbModel = SQLite.select().from(DbModel.class).querySingle();
      Timber.d("loading weather from the db!");
      mainMvpView.showWeather(new Gson().fromJson(dbModel.getCurrent_weather(), WeatherPojo.class));
    }else{
      Timber.d("nothing in the database");
    }
  }

  private static boolean isHttp404(Throwable error) {
    return error instanceof HttpException && ((HttpException) error).code() == 404;
  }
}