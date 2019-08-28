import PropTypes from 'prop-types';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import MomentTZ from 'moment-timezone';
import Moment from 'moment';
import onClickOutside from 'react-onclickoutside';
import _ from 'lodash';

import TetheredDateTime from './TetheredDateTime';
import { addNotification } from '../../actions/AppActions';
import {
  allowOutsideClick,
  disableOutsideClick,
} from '../../actions/WindowActions';

class DatePicker extends Component {
  static timeZoneRegex = new RegExp(/[+-]{1}\d+:\d+/);

  constructor(props) {
    super(props);
    this.state = {
      open: false,
      cache: null,
      // we need to store a local copy of value in case we need to strip it out of timezone
      value: null,
    };
  }

  componentDidMount() {
    const { handleBackdropLock, isOpenDatePicker, value } = this.props;
    handleBackdropLock && handleBackdropLock(true);

    this.setState({
      value: value || null,
    });

    if (isOpenDatePicker) {
      setTimeout(() => {
        this.picker.openCalendar();
      }, 100);
    }
  }

  UNSAFE_componentWillReceiveProps(nextProps) {
    const { hasTimeZone } = this.props;
    let { value } = nextProps;

    if (hasTimeZone && value) {
      if (typeof value !== 'string') {
        value = value.format();
      }

      const timeZoneOffset = value.match(DatePicker.timeZoneRegex)[0];

      const timeZone = _.find(MomentTZ.tz.names(), timezoneName => {
        return timeZoneOffset === MomentTZ.tz(timezoneName).format('Z');
      });

      value = value.replace(DatePicker.timeZoneRegex, '');
      value = MomentTZ.tz(value, timeZone);
    } else {
      if (!value) value = null;

      if (value && !Moment.isMoment(value) && this.props.dateFormat) {
        value = new Date(value);
      }
    }

    this.setState({
      value,
    });
  }

  handleBlur = date => {
    const { patch, handleBackdropLock, dispatch, field } = this.props;
    const { cache, open } = this.state;

    if (!open) {
      return;
    }

    try {
      if (
        JSON.stringify(cache) !==
        (date !== '' ? JSON.stringify(date && date.toDate()) : '')
      ) {
        patch(date);
      }
    } catch (error) {
      dispatch(
        addNotification(field, `${field} has an invalid date.`, 5000, 'error')
      );
    }

    this.handleClose();

    handleBackdropLock && handleBackdropLock(false);
  };

  handleFocus = () => {
    const { dispatch } = this.props;
    const { value } = this.state;

    this.setState({
      cache: value,
      open: true,
    });
    dispatch(disableOutsideClick());
  };

  handleClose = () => {
    const { dispatch } = this.props;
    this.setState({
      open: false,
    });
    dispatch(allowOutsideClick());
  };

  handleClickOutside = () => {
    const { open } = this.state;

    if (!open) {
      return;
    }
    this.handleBlur(this.picker.state.selectedDate);
  };

  handleKeydown = e => {
    e.stopPropagation();
  };

  renderDay = (props, currentDate) => {
    return (
      <td {...props} onDoubleClick={() => this.handleBlur(currentDate)}>
        {currentDate.date()}
      </td>
    );
  };

  focusInput = () => {
    this.inputElement && this.inputElement.focus();
  };

  renderInput = ({ className, ...props }) => (
    <div className={className}>
      <input
        {...props}
        className="form-control"
        ref={input => {
          this.inputElement = input;
        }}
      />
    </div>
  );

  render() {
    const { value } = this.state;

    return (
      <div tabIndex="-1" onKeyDown={this.handleKeydown} className="datepicker">
        <TetheredDateTime
          ref={c => (this.picker = c)}
          closeOnTab={true}
          renderDay={this.renderDay}
          renderInput={this.renderInput}
          onBlur={this.handleBlur}
          onFocus={this.handleFocus}
          open={this.state.open}
          onFocusInput={this.focusInput}
          closeOnSelect={false}
          {...this.props}
          value={value}
        />
        <i className="meta-icon-calendar" key={0} />
      </div>
    );
  }
}

DatePicker.propTypes = {
  dispatch: PropTypes.func.isRequired,
  handleBackdropLock: PropTypes.func,
  patch: PropTypes.func,
  field: PropTypes.string,
  value: PropTypes.any,
  isOpenDatePicker: PropTypes.bool,
  hasTimeZone: PropTypes.bool,
  dateFormat: PropTypes.oneOfType([PropTypes.bool, PropTypes.string]),
};

export default connect()(onClickOutside(DatePicker));
