package com.kot32.ksimplelibrary.widgets.view;

import android.content.Context;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Scroller;
import android.widget.TextView;

import com.kot32.ksimplelibrary.util.tools.DisplayUtil;


/**
 * Created by kot32 on 15/10/18.
 */
public class KRefreshView extends LinearLayout {

    private Scroller scroller = new Scroller(getContext());

    private TextView text;

    private boolean shouldRefresh = false;

    private boolean isRefreshing = false;

    //头部刷新View的父容器
    private RelativeLayout headerContent;
    //头部刷新View
    private View headerView;
    //控制UI更新
    private RefreshViewHolder refreshViewHolder;
    //控制事务更新
    private IRefreshAction iRefreshAction;
    //触摸事件传递接口
    private onRefreshViewTouch onRefreshViewTouch;
    //默认高度
    private int HEADER_HEIGHT = 100;

    private int MAX_LIMIT_SOLT = 50;

    private int totalLimit;

    public KRefreshView(Context context) {
        super(context);
        initView();
        initData();
    }

    public KRefreshView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
        initData();
    }

    private void initView() {
        setOrientation(VERTICAL);

        //增加头部View容器
        LinearLayout.LayoutParams contentLayout = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, DisplayUtil.dip2px(getContext(), HEADER_HEIGHT));
        headerContent = new RelativeLayout(getContext());
        contentLayout.setMargins(0, -DisplayUtil.dip2px(getContext(), HEADER_HEIGHT), 0, 0);
        headerContent.setLayoutParams(contentLayout);
        addView(headerContent, 0);

        //增加隐藏的View
        text = new TextView(getContext());
        text.setText("继续下拉以刷新...");
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        text.setLayoutParams(layoutParams);
        headerContent.addView(text, 0);
    }

    private void initData() {
        refreshViewHolder = new RefreshViewHolder() {

            @Override
            public void pullingTips(View headerView, int progress) {
                text.setText("继续下拉以刷新...");
            }

            @Override
            public void willRefreshTips(View headerView) {

                text.setText("松开以刷新");
            }

            @Override
            public void refreshingTips(View headerView) {
                text.setText("正在刷新...");
            }

            @Override
            public void refreshCompleteTips(View headerView) {
                text.setText("刷新成功");
            }
        };

        totalLimit = DisplayUtil.dip2px(getContext(), HEADER_HEIGHT + MAX_LIMIT_SOLT);
    }

    private float preY;
    private float tmpY;


    private float Y1;
    private float Y2;

    //判断是否拦截事件
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        //如果是方向向上，且ListView
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Y1 = ev.getRawY();
                preY = ev.getRawY();
                tmpY = ev.getRawY();
                break;
            case MotionEvent.ACTION_MOVE:
                Y2 = ev.getRawY();
                if (Y2 > Y1) {
                    View view = getChildAt(1);
                    if (view instanceof ListView) {
                        //如果ListView可见的第一个index是0，并且还没滑动
                        if (((ListView) view).getFirstVisiblePosition() == 0) {
                            View v = ((ListView) view).getChildAt(0);
                            if ((v == null) || (v != null && v.getTop() <= 0))
                                return true;
                        }
                    } else if (view instanceof ScrollView) {
                        if (view.getScrollY() == 0) {
                            return true;
                        }
                    } else if (view instanceof WebView) {
                        System.out.println(view.getScrollY());
                        if (view.getScrollY() == 0) {
                            return true;
                        }
                    }

                }

                break;
        }
        return false;

    }


    @Override
    public boolean onTouchEvent(MotionEvent ev) {

        boolean isTiping = false;

        if (onRefreshViewTouch != null) {
            onRefreshViewTouch.onTouch((int) ev.getRawX(), (int) ev.getRawY());
        }

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                preY = ev.getRawY();
                tmpY = ev.getRawY();

                isTiping = false;

                if (refreshViewHolder != null) {
                    if (isRefreshing) break;
                    refreshViewHolder.pullingTips(headerView, 0);
                }
                break;
            case MotionEvent.ACTION_MOVE:

                float currentY = ev.getRawY();
                float offsetY = currentY - tmpY;
                float dis = currentY - preY;

                if (dis >= DisplayUtil.dip2px(getContext(), HEADER_HEIGHT)) {
                    if (refreshViewHolder != null) {
                        if (!isTiping) {
                            //提示只有一次
                            refreshViewHolder.willRefreshTips(headerView);
                            isTiping = true;
                        }
                    }
                    shouldRefresh = true;
                } else {
                    shouldRefresh = false;
                    float ratio = dis / DisplayUtil.dip2px(getContext(), HEADER_HEIGHT);
                    if (refreshViewHolder != null && !isRefreshing)
                        refreshViewHolder.pullingTips(headerView, (int) (100 * ratio));
                }

                if (dis >= 0 && (dis < totalLimit)) {
                    this.scrollBy(0, -(int) offsetY);
                }
                if (dis >= totalLimit) {
                    this.scrollTo(0, -totalLimit);
                }

                tmpY = currentY;

                break;
            case MotionEvent.ACTION_UP:
                //复原
                if (shouldRefresh)
                    startRefresh();
                else {
                    smoothScrollTo(0, 0);
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                //复原
                if (shouldRefresh)
                    startRefresh();
                else {
                    smoothScrollTo(0, 0);
                }
                break;
        }

        return true;
    }


    private void startRefresh() {
        if (refreshViewHolder != null) {
            refreshViewHolder.refreshingTips(headerView);
            //开始后台刷新任务
            if (!isRefreshing)
                new RefreshTask().execute();
        }
    }

    private void smoothScrollTo(int destX, int destY) {
        int offsetY = destY - getScrollY();
        scroller.startScroll(0, getScrollY(), 0, offsetY, 500);
        invalidate();
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), scroller.getCurrY());
            postInvalidate();
        }
    }


    //管理UI方面的接口
    public interface RefreshViewHolder {

        //还没到刷新点的提示
        void pullingTips(View headerView, int progress);

        //快要刷新时的提示
        void willRefreshTips(View headerView);

        //正在刷新时的状态
        void refreshingTips(View headerView);

        //刷新完毕
        void refreshCompleteTips(View headerView);
    }

    //管理事务方面的接口
    public interface IRefreshAction {

        void refresh();

        void refreshComplete();
    }

    //触摸点传递的接口，可供实现类扩展更多自定义动画
    public interface onRefreshViewTouch {
        void onTouch(int x, int y);
    }


    public View getHeaderView() {
        return headerView;
    }

    public void setHeaderView(View headerView, RelativeLayout.LayoutParams layoutParams) {
        this.headerView = headerView;
        headerContent.removeViewAt(0);
        RelativeLayout.LayoutParams defaultLayoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        defaultLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        if (layoutParams == null)
            headerView.setLayoutParams(defaultLayoutParams);
        else
            headerView.setLayoutParams(layoutParams);
        headerContent.addView(headerView, 0);
    }

    public RefreshViewHolder getRefreshViewHolder() {
        return refreshViewHolder;
    }

    public void setRefreshViewHolder(RefreshViewHolder refreshViewHolder) {
        this.refreshViewHolder = refreshViewHolder;
    }

    private class RefreshTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {

            isRefreshing = true;
            shouldRefresh = false;

            if (iRefreshAction != null) {
                iRefreshAction.refresh();
            }
            return null;
        }


        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            post(new Runnable() {
                @Override
                public void run() {
                    if (refreshViewHolder != null) {
                        refreshViewHolder.refreshCompleteTips(headerView);
                        smoothScrollTo(0, 0);
                    }
                }
            });
            if (iRefreshAction != null) {
                iRefreshAction.refreshComplete();
            }

            isRefreshing = false;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            smoothScrollTo(0, 0);
            shouldRefresh = false;
            isRefreshing = false;
        }
    }

    public void setiRefreshAction(IRefreshAction iRefreshAction) {
        this.iRefreshAction = iRefreshAction;
    }

    public KRefreshView.onRefreshViewTouch getOnRefreshViewTouch() {
        return onRefreshViewTouch;
    }

    public void setOnRefreshViewTouch(KRefreshView.onRefreshViewTouch onRefreshViewTouch) {
        this.onRefreshViewTouch = onRefreshViewTouch;
    }

    public void setHeaderHeight(int headerHeightDp) {
        this.HEADER_HEIGHT = headerHeightDp;
        totalLimit = DisplayUtil.dip2px(getContext(), HEADER_HEIGHT + MAX_LIMIT_SOLT);
    }

    public int getMAX_LIMIT_SOLT() {
        return MAX_LIMIT_SOLT;
    }

    public void setMAX_LIMIT_SOLT(int MAX_LIMIT_SOLT) {
        this.MAX_LIMIT_SOLT = MAX_LIMIT_SOLT;
        totalLimit = DisplayUtil.dip2px(getContext(), HEADER_HEIGHT + MAX_LIMIT_SOLT);
    }

}
