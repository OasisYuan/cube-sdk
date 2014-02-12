package com.srain.cube.sample.activity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.srain.cube.app.CubeFragment;
import com.srain.cube.sample.R;
import com.srain.cube.sample.ui.views.header.TitleHeaderBar;

/**
 * 带页头的 Activity基类
 * 
 * <p>
 * 使用一个orientation="vertical", LinearLayout，包含一个统一的页头{@link TitleHeaderBar} , 内容置于页头下部
 * 
 * <p>
 * <a href="http://www.liaohuqiu.net/unified-title-header/">http://www.liaohuqiu.net/unified-title-header/</a>
 * 
 * @author huqiu.lhq
 */
public abstract class TitleBaseFragment extends CubeFragment {

	protected TitleHeaderBar mTitleHeaderBar;

	protected abstract View createView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState);

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		LinearLayout view = (LinearLayout) inflater.inflate(R.layout.fragment_title_base, null);

		// 页头逻辑处理
		mTitleHeaderBar = (TitleHeaderBar) view.findViewById(R.id.ly_header_bar_title_wrap);
		if (enableDefaultBack()) {
			mTitleHeaderBar.getLeftTextView().setText(R.string.base_title_return);
			mTitleHeaderBar.setLeftOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					onBackPressed();
				}
			});
		} else {
			mTitleHeaderBar.getLeftViewContainer().setVisibility(View.INVISIBLE);
		}

		View contentView = createView(inflater, container, savedInstanceState);
		contentView.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
		view.addView(contentView);
		return view;
	}

	private void onBackPressed() {
		getContext().onBackPressed();
	}

	/**
	 * 是否使用默认的返回处理
	 * 
	 * @return
	 */
	protected boolean enableDefaultBack() {
		return true;
	}

	/**
	 * 设置标题
	 * 
	 * @param id
	 */
	protected void setHeaderTitle(int id) {
		mTitleHeaderBar.getTitleTextView().setText(id);
	}

	/**
	 * 设置标题
	 * 
	 * @param id
	 */
	protected void setHeaderTitle(String title) {
		mTitleHeaderBar.getTitleTextView().setText(title);
	}

	public TitleHeaderBar getTitleHeaderBar() {
		return mTitleHeaderBar;
	}

}
